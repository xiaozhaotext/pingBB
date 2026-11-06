package com.macro.mall.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CkirWorkbookReaderTest {
    @Test
    void readsTheSuppliedCkirWorkbookWhenAvailable() throws Exception {
        String sample = System.getenv("CKIR_SOURCE_XLSX");
        Assumptions.assumeTrue(sample != null && Files.isRegularFile(Path.of(sample)));
        var file = new MockMultipartFile("file", "ckir.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.newInputStream(Path.of(sample)));
        try (var result = CkirWorkbookReader.read(file)) {
            assertTrue(result.items().size() > 300);
            assertEquals("组合装", result.items().get(0).sheet());
            assertNotNull(result.items().get(0).imageEntry());
            assertTrue(CkirWorkbookReader.image(result.temporaryFile(), result.items().get(0).imageEntry()).length > 1000);
        }
    }

    @Test
    void readsProductsFromEveryCategorySheet() throws Exception {
        byte[] data;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            for (String sheetName : new String[]{"CKIR家纺", "CKIR女包"}) {
                var sheet = book.createSheet(sheetName);
                var header = sheet.createRow(0);
                header.createCell(0).setCellValue("产品名称");
                header.createCell(1).setCellValue("零售价");
                header.createCell(2).setCellValue("产品型号");
                var row = sheet.createRow(1);
                row.createCell(0).setCellValue(sheetName + "商品");
                row.createCell(1).setCellValue(199);
                row.createCell(2).setCellValue("CKIR-001");
            }
            book.write(out);
            data = out.toByteArray();
        }
        var file = new MockMultipartFile("file", "ckir.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data);
        try (var result = CkirWorkbookReader.read(file)) {
            assertEquals(2, result.items().size());
            assertEquals("CKIR家纺", result.items().get(0).sheet());
            assertEquals("CKIR女包商品", result.items().get(1).get("产品名称"));
            assertEquals("199", result.items().get(1).get("零售价"));
        }
    }
}
