package com.macro.mall.service;

import com.macro.mall.dao.CmsPrefrenceAreaProductRelationDao;
import com.macro.mall.dao.CmsSubjectProductRelationDao;
import com.macro.mall.dao.PmsMemberPriceDao;
import com.macro.mall.dao.PmsProductAttributeValueDao;
import com.macro.mall.dao.PmsProductFullReductionDao;
import com.macro.mall.dao.PmsProductLadderDao;
import com.macro.mall.dao.PmsProductVertifyRecordDao;
import com.macro.mall.dao.PmsSkuStockDao;
import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.mapper.CmsPrefrenceAreaProductRelationMapper;
import com.macro.mall.mapper.CmsSubjectProductRelationMapper;
import com.macro.mall.mapper.PmsMemberPriceMapper;
import com.macro.mall.mapper.PmsProductAttributeValueMapper;
import com.macro.mall.mapper.PmsProductFullReductionMapper;
import com.macro.mall.mapper.PmsProductLadderMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品保存时 SKU 价格/库存必须与商品维度保持一致。
 *
 * 背景（2026-10-13 线上实测）：
 *   - 后台「商品属性」步骤生成的 SKU 只写销售属性，price/stock 为空，
 *     应用端详情页无价、加购与下单都提示「库存不足」。
 *   - 应用端首页/列表读 pms_product.price，详情页与购物车读 pms_sku_stock.price，
 *     后台只改商品价时 SKU 价不跟随，出现「首页已改价、详情与购物车还是旧价」。
 */
@ExtendWith(MockitoExtension.class)
class PmsProductServiceImplTest {
    @Mock PmsProductMapper productMapper;
    @Mock PmsMemberPriceDao memberPriceDao;
    @Mock PmsMemberPriceMapper memberPriceMapper;
    @Mock PmsProductLadderDao productLadderDao;
    @Mock PmsProductLadderMapper productLadderMapper;
    @Mock PmsProductFullReductionDao productFullReductionDao;
    @Mock PmsProductFullReductionMapper productFullReductionMapper;
    @Mock PmsSkuStockDao skuStockDao;
    @Mock PmsSkuStockMapper skuStockMapper;
    @Mock PmsProductAttributeValueDao productAttributeValueDao;
    @Mock PmsProductAttributeValueMapper productAttributeValueMapper;
    @Mock CmsSubjectProductRelationDao subjectProductRelationDao;
    @Mock CmsSubjectProductRelationMapper subjectProductRelationMapper;
    @Mock CmsPrefrenceAreaProductRelationDao prefrenceAreaProductRelationDao;
    @Mock CmsPrefrenceAreaProductRelationMapper prefrenceAreaProductRelationMapper;
    @Mock com.macro.mall.dao.PmsProductDao productDao;
    @Mock PmsProductVertifyRecordDao productVertifyRecordDao;

    @InjectMocks
    PmsProductServiceImpl service;

    private static PmsProduct originProduct(long id, String price) {
        PmsProduct origin = new PmsProduct();
        origin.setId(id);
        origin.setPrice(new BigDecimal(price));
        return origin;
    }

    private static PmsSkuStock sku(Long id, String price, Integer stock, Integer lockStock) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(id);
        if (price != null) {
            sku.setPrice(new BigDecimal(price));
        }
        sku.setStock(stock);
        sku.setLockStock(lockStock);
        sku.setSpData("[{\"key\":\"颜色\",\"value\":\"黑色\"}]");
        return sku;
    }

    private static PmsProductParam param(String price, Integer stock, Integer publishStatus, List<PmsSkuStock> skuList) {
        PmsProductParam param = new PmsProductParam();
        param.setPrice(new BigDecimal(price));
        param.setStock(stock);
        param.setPublishStatus(publishStatus);
        param.setSkuStockList(skuList);
        return param;
    }

    @Test
    void updateFillsBlankSkuPriceAndStockFromProduct() {
        PmsProductParam param = param("998.00", 100, 1, new ArrayList<>(
                List.of(sku(null, null, null, null))));
        when(productMapper.selectByPrimaryKey(27L)).thenReturn(originProduct(27L, "998.00"));
        when(skuStockMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        service.update(27L, param);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PmsSkuStock>> captor = ArgumentCaptor.forClass(List.class);
        verify(skuStockDao).insertList(captor.capture());
        PmsSkuStock inserted = captor.getValue().get(0);
        assertEquals(0, new BigDecimal("998.00").compareTo(inserted.getPrice()));
        assertEquals(100, inserted.getStock());
        assertEquals(0, inserted.getLockStock(), "锁定库存必须落成 0，NULL 会让下单预占库存失败");
        assertNotNull(inserted.getSkuCode(), "SKU 编码应自动生成");
    }

    @Test
    void updateSyncsSkuPriceWhenProductPriceChanges() {
        PmsSkuStock existing = sku(98L, "2699.00", 86, -24);
        PmsProductParam param = param("998.00", 100, 1, new ArrayList<>(List.of(existing)));
        when(productMapper.selectByPrimaryKey(27L)).thenReturn(originProduct(27L, "2699.00"));
        when(skuStockMapper.selectByExample(any())).thenReturn(new ArrayList<>(List.of(sku(98L, "2699.00", 86, -24))));

        service.update(27L, param);

        ArgumentCaptor<PmsSkuStock> captor = ArgumentCaptor.forClass(PmsSkuStock.class);
        verify(skuStockMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals(0, new BigDecimal("998.00").compareTo(captor.getValue().getPrice()),
                "商品改价后 SKU 价必须跟随，否则应用端详情页与购物车还是旧价");
    }

    @Test
    void updateKeepsSkuPriceWhenProductPriceUnchanged() {
        PmsSkuStock existing = sku(98L, "2699.00", 86, 0);
        PmsProductParam param = param("2699.00", 100, 1, new ArrayList<>(List.of(existing)));
        when(productMapper.selectByPrimaryKey(27L)).thenReturn(originProduct(27L, "2699.00"));
        when(skuStockMapper.selectByExample(any())).thenReturn(new ArrayList<>(List.of(sku(98L, "2699.00", 86, 0))));

        service.update(27L, param);

        ArgumentCaptor<PmsSkuStock> captor = ArgumentCaptor.forClass(PmsSkuStock.class);
        verify(skuStockMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals(0, new BigDecimal("2699.00").compareTo(captor.getValue().getPrice()));
    }

    @Test
    void updateCreatesDefaultSkuWhenPublishedWithoutSpecs() {
        PmsProductParam param = param("369.00", 50, 1, new ArrayList<>());
        param.setLowStock(5);
        when(productMapper.selectByPrimaryKey(35L)).thenReturn(originProduct(35L, "369.00"));
        when(skuStockMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        service.update(35L, param);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PmsSkuStock>> captor = ArgumentCaptor.forClass(List.class);
        verify(skuStockDao).insertList(captor.capture());
        List<PmsSkuStock> inserted = captor.getValue();
        assertEquals(1, inserted.size());
        assertEquals(0, new BigDecimal("369.00").compareTo(inserted.get(0).getPrice()));
        assertEquals(50, inserted.get(0).getStock());
        assertEquals(0, inserted.get(0).getLockStock());
        assertEquals("[]", inserted.get(0).getSpData(), "空规格数组才能让应用端直接命中这条 SKU");
    }

    @Test
    void updateLeavesUnpublishedProductWithoutSku() {
        PmsProductParam param = param("369.00", 50, 0, new ArrayList<>());
        when(productMapper.selectByPrimaryKey(35L)).thenReturn(originProduct(35L, "369.00"));

        service.update(35L, param);

        verify(skuStockDao, never()).insertList(any());
    }
}