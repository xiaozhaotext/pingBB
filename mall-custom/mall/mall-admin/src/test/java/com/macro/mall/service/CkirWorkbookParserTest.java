package com.macro.mall.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CkirWorkbookParserTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "CKIR_SAMPLE", matches = ".+")
    void parsesRealMultiSheetWorkbook() throws Exception {
        var result = new CkirWorkbookParser().parse(Path.of(System.getenv("CKIR_SAMPLE")));
        System.out.println("CKIR parsed=" + result.items().size() + " warnings=" + result.warnings().size());
        result.warnings().stream().limit(15).forEach(System.out::println);
        assertFalse(result.items().isEmpty());
    }
}
