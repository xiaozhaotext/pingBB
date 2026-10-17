package com.macro.mall.portal;

import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.PromotionProduct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by macro on 2018/8/27.
 * 前台商品查询逻辑单元测试
 */
@SpringBootTest
public class PortalProductDaoTests extends IsolatedPortalMysqlTestSupport {
    @Autowired
    private PortalProductDao portalProductDao;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.junit.jupiter.api.BeforeEach
    void seedCatalog() {
        for (long id = 26; id <= 29; id++) {
            jdbc.update("INSERT INTO pms_product(id,name,product_sn,publish_status,delete_status,promotion_type) VALUES(?,?,?,1,0,0) ON DUPLICATE KEY UPDATE name=VALUES(name)", id, "isolated-catalog-" + id, "isolated-sku-" + id);
        }
    }
    @Test
    public void testGetPromotionProductList(){
        List<Long> ids = new ArrayList<>();
        ids.add(26L);
        ids.add(27L);
        ids.add(28L);
        ids.add(29L);
        List<PromotionProduct> promotionProductList = portalProductDao.getPromotionProductList(ids);
        assertEquals(4,promotionProductList.size());
    }
}
