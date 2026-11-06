package com.macro.mall.service;

import com.macro.mall.dto.PmsProductParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CkirProductImportServiceTest {
    @TempDir Path temp;

    @Test
    void createsSeparateUnpublishedZeroStockProductsWithZonePrices() throws Exception {
        var parser = mock(CkirWorkbookParser.class);
        var jdbc = mock(JdbcTemplate.class);
        var products = mock(PmsProductService.class);
        var item = new CkirWorkbookParser.Item("CKIR家纺", 2, "测试毛巾", "CKIR-M1",
                "纯棉毛巾", new BigDecimal("69.00"), new BigDecimal("99.00"), "xl/media/test.jpeg");
        when(parser.parse(any(Path.class))).thenReturn(new CkirWorkbookParser.Preview(List.of(item), List.of()));
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any())).thenReturn(List.of(7L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        var service = new CkirProductImportService(parser, jdbc, products);
        ReflectionTestUtils.setField(service, "imageDir", temp.resolve("images").toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/media/test.jpeg"));
            zip.write(new byte[] {(byte)0xff, (byte)0xd8, (byte)0xff, (byte)0xd9});
            zip.closeEntry();
        }
        var file = new MockMultipartFile("file", "CKIR.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        var result = service.importFile(file);
        var capture = org.mockito.ArgumentCaptor.forClass(PmsProductParam.class);
        verify(products, times(2)).create(capture.capture());
        var created = capture.getAllValues();
        assertEquals(2, result.createdProducts());
        assertEquals(List.of("REBUY", "CONSUME"), created.stream().map(PmsProductParam::getZoneCode).toList());
        assertEquals(List.of(new BigDecimal("69.00"), new BigDecimal("99.00")),
                created.stream().map(PmsProductParam::getPrice).toList());
        for (var product : created) {
            assertEquals(0, product.getStock());
            assertEquals(0, product.getPublishStatus());
            assertTrue(product.getPic().startsWith("/product-images/ckir-"));
            assertEquals(0, product.getSkuStockList().get(0).getStock());
        }
    }
}
