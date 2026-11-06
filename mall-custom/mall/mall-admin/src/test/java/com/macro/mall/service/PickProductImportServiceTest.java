package com.macro.mall.service;

import com.macro.mall.dto.PmsProductParam;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickProductImportServiceTest {
    @Test
    void csvQuotesAndLineBreaksStayInOneRoute() {
        var rows = PickProductImportService.parseCsv("a,b\n\"云南,六日游\",\"第1天\n第2天\"\n");
        assertEquals(2, rows.size());
        assertEquals("云南,六日游", rows.get(1).get(0));
        assertEquals("第1天\n第2天", rows.get(1).get(1));
    }

    @Test
    void importsDraftTourismProductAtFixedPriceAndStock() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), any())).thenReturn(0);
        PmsProductService products = mock(PmsProductService.class);
        when(jdbc.queryForList(contains("pms_product_category"), eq(Long.class))).thenReturn(List.of(77L));
        when(jdbc.queryForList(contains("pms_product_attribute_category"), eq(Long.class))).thenReturn(List.of(16L));
        var service = new PickProductImportService(jdbc, products);
        String csv = "商品名称,副标题,商品货号,库存,封面图URL,行程简介,行程详情\n"
                + "云南6日游,雪山与古城,TOUR-001,5,https://example.com/view.jpg,6天5晚,第1天抵达丽江\n";
        var file = new MockMultipartFile("file", "routes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var result = service.importFile(file);
        assertEquals(1, result.imported());
        var capture = org.mockito.ArgumentCaptor.forClass(PmsProductParam.class);
        verify(products).create(capture.capture());
        var product = capture.getValue();
        assertEquals("PICK", product.getZoneCode());
        assertEquals("旅游路线", product.getProductCategoryName());
        assertEquals(0, product.getPublishStatus());
        assertEquals(0, product.getPromotionType());
        assertEquals(3999, product.getPrice().intValue());
        assertEquals(3999, product.getSkuStockList().get(0).getPrice().intValue());
        assertEquals(5, product.getSkuStockList().get(0).getStock());
    }

    @Test
    void acceptsExcelAndRejectsBadInventoryBeforeCreatingAnything() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), any())).thenReturn(0);
        PmsProductService products = mock(PmsProductService.class);
        var service = new PickProductImportService(jdbc, products);
        byte[] excel;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("路线");
            String[] headers = {"商品名称", "副标题", "商品货号", "库存", "封面图URL", "行程简介", "行程详情"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            String[] values = {"三亚5日游", "海岸行程", "TOUR-002", "0", "https://example.com/sea.jpg", "5天4晚", "第1天抵达三亚"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            workbook.write(output);
            excel = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "routes.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.importFile(file));
        assertTrue(error.getReason().contains("库存"));
        verifyNoInteractions(products);
    }

    @Test
    void importsRebuyProductWithSpreadsheetPriceAndCategory() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), any())).thenReturn(0);
        PmsProductService products = mock(PmsProductService.class);
        when(jdbc.queryForList(contains("show_status=1"), eq(Long.class), eq("日用品"), eq("REBUY"))).thenReturn(List.of(42L));
        var service = new PickProductImportService(jdbc, products);
        String csv = "商品分类,商品名称,副标题,商品货号,售价,库存,封面图URL,商品简介,商品详情\n"
                + "日用品,旅行水杯,便携水杯,CUP-001,19.90,6,https://example.com/cup.jpg,随行水杯,容量500毫升\n";
        var file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, service.importFile("REBUY", file).imported());
        var capture = org.mockito.ArgumentCaptor.forClass(PmsProductParam.class);
        verify(products).create(capture.capture());
        assertEquals("REBUY", capture.getValue().getZoneCode());
        assertEquals(42L, capture.getValue().getProductCategoryId());
        assertEquals(new java.math.BigDecimal("19.90"), capture.getValue().getSkuStockList().get(0).getPrice());
        assertEquals(0, capture.getValue().getPublishStatus());
    }

    @Test
    void rejectsUnknownCategoryBeforeImport() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Integer.class), any())).thenReturn(0);
        PmsProductService products = mock(PmsProductService.class);
        var service = new PickProductImportService(jdbc, products);
        String csv = "商品分类,商品名称,副标题,商品货号,售价,库存,封面图URL,商品简介,商品详情\n"
                + "不存在,旅行水杯,便携水杯,CUP-001,19.90,6,https://example.com/cup.jpg,随行水杯,容量500毫升\n";
        var file = new MockMultipartFile("file", "products.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.importFile("CONSUME", file));
        assertTrue(error.getReason().contains("商品分类"));
        verifyNoInteractions(products);
    }
}
