package com.macro.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.service.P0AdminAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ColumnImageControllerTest {
    @TempDir Path directory;

    @Test
    void eachTourismNameIsSavedWithoutChangingTheOthers() throws Exception {
        P0AdminAccess access = mock(P0AdminAccess.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Principal principal = () -> "admin";
        when(access.requirePlatformAdmin(principal)).thenReturn(1L);
        ColumnImageController controller = new ColumnImageController(access, jdbc, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "directory", directory.toString());

        controller.saveTourismLabel(principal, Map.of("label", "智游美"));
        controller.saveTourismLabels(principal, 1, Map.of("name", "云南游"));
        controller.saveTourismLabels(principal, 2, Map.of("name", "海岛游"));

        Map<?, ?> saved = new ObjectMapper().readValue(Files.readString(directory.resolve("tourism-label.json")), Map.class);
        assertEquals("智游美", saved.get("label"));
        assertEquals(List.of("云南游", "海岛游", "智游美3", "智游美4"), saved.get("names"));
        controller.saveTourismLabel(principal, Map.of("label", "旅行好礼"));
        saved = new ObjectMapper().readValue(Files.readString(directory.resolve("tourism-label.json")), Map.class);
        assertEquals("旅行好礼", saved.get("label"));
        assertEquals(List.of("云南游", "海岛游", "智游美3", "智游美4"), saved.get("names"));
        assertThrows(IllegalArgumentException.class,
                () -> controller.saveTourismLabels(principal, 5, Map.of("name", "无效")));
    }
}
