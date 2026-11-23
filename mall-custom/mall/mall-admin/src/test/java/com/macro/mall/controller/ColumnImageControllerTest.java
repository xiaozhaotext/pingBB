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
    void miniHomeSettingsRejectInvalidValuesAndKeepPreviousPublish() throws Exception {
        P0AdminAccess access = mock(P0AdminAccess.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Principal principal = () -> "admin";
        when(access.requirePlatformAdmin(principal)).thenReturn(1L);
        ColumnImageController controller = new ColumnImageController(access, jdbc, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "directory", directory.toString());

        controller.saveHomeCopy(principal, Map.of("brand", "旅行商城"));
        assertEquals("旅行商城", controller.homeCopy(principal).getData().get("brand"));
        assertThrows(IllegalArgumentException.class,
                () -> controller.saveHomeCopy(principal, Map.of("brand", "  ")));
        assertEquals("旅行商城", controller.homeCopy(principal).getData().get("brand"));

        Map<String,Object> layout = Map.of("showHero", true, "showAnnouncement", true,
                "showQuickLinks", true, "showPick", true, "showRebuy", true,
                "showConsume", false, "zoneOrder", List.of("rebuy", "consume", "pick"));
        controller.saveHomeLayout(principal, layout);
        assertEquals(false, controller.homeLayout(principal).getData().get("showConsume"));
        Map<String,Object> invalid = new java.util.HashMap<>(layout);
        invalid.put("zoneOrder", List.of("pick", "pick", "consume"));
        assertThrows(IllegalArgumentException.class, () -> controller.saveHomeLayout(principal, invalid));
        assertEquals(List.of("rebuy", "consume", "pick"), controller.homeLayout(principal).getData().get("zoneOrder"));

        Map<String,String> links = Map.of("brand", "/pages/index/index", "topCart", "/pages/cart/cart",
                "quickCategory", "/pages/category/category", "quickCart", "/pages/cart/cart",
                "pick", "/pages/zone/pick", "rebuy", "/pages/zone/rebuy", "consume", "/pages/zone/consume");
        controller.saveHomeLinks(principal, links);
        Map<String,String> unsafe = new java.util.HashMap<>(links);
        unsafe.put("pick", "https://other.example");
        assertThrows(IllegalArgumentException.class, () -> controller.saveHomeLinks(principal, unsafe));
        assertEquals("/pages/zone/pick", controller.homeLinks(principal).getData().get("pick"));
    }

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
