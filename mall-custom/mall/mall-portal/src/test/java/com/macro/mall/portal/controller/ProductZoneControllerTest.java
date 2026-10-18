package com.macro.mall.portal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductZoneControllerTest {
    @Test void pickCatalogRequiresApproved3999RouteAndSkus() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.<Map<String,Object>>of());
        new ProductZoneController(jdbc).list("PICK",null,null,"latest");
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(Object[].class));
        assertTrue(sql.getValue().contains("p.price=3999.00"));
        assertTrue(sql.getValue().contains("tri_membership_product"));
        assertTrue(sql.getValue().contains("NOT EXISTS (SELECT 1 FROM pms_sku_stock"));
    }
}
