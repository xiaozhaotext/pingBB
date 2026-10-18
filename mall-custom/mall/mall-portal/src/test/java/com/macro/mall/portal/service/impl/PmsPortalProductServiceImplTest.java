package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.PmsCommentMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.dao.PortalProductDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PmsPortalProductServiceImplTest {
    @Mock PmsProductMapper productMapper;
    @Mock PmsProductAttributeMapper productAttributeMapper;
    @Mock PmsSkuStockMapper skuStockMapper;
    @Mock PmsCommentMapper commentMapper;
    @Mock PortalProductDao portalProductDao;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks PmsPortalProductServiceImpl service;

    @Test
    void publishedTourWithoutAttributeCategoryStillHasDetail() {
        PmsProduct tour = new PmsProduct();
        tour.setId(101L);
        tour.setName("云南雪山湖泊6日游");
        tour.setDeleteStatus(0);
        tour.setPublishStatus(1);
        tour.setPromotionType(0);
        tour.setProductAttributeCategoryId(null);
        when(productMapper.selectByPrimaryKey(101L)).thenReturn(tour);

        var detail = service.detail(101L);

        assertNotNull(detail);
        assertNotNull(detail.getProduct());
        assertTrue(detail.getProductAttributeList().isEmpty());
        assertTrue(detail.getProductAttributeValueList().isEmpty());
        verify(productAttributeMapper, never()).selectByExample(any());
    }
}
