package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.ZoneCartItem;
import com.macro.mall.portal.domain.ZoneRules;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车管理Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public int add(OmsCartItem cartItem, String zoneCode) {
        int count;
        if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1 || cartItem.getQuantity() > 99) {
            Asserts.fail("商品数量无效");
        }
        refreshFromCatalog(cartItem);
        // 不同专区的同一商品是两条购物车记录，专区规则不能相互串用
        String resolvedZone = resolveCartZone(cartItem.getProductId(), zoneCode);
        UmsMember currentMember =memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        OmsCartItem existCartItem = getCartItem(cartItem, resolvedZone);
        if (existCartItem == null) {
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
            jdbc.update("UPDATE oms_cart_item SET zone_code=? WHERE id=?", resolvedZone, cartItem.getId());
        } else {
            cartItem.setModifyDate(new Date());
            int newQuantity = existCartItem.getQuantity() + cartItem.getQuantity();
            ensureStock(cartItem.getProductSkuId(), newQuantity);
            existCartItem.setQuantity(newQuantity);
            existCartItem.setPrice(cartItem.getPrice());
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
        }
        return count;
    }

    /**
     * 根据会员id,商品id,规格和专区获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem, String zoneCode) {
        String sql = "SELECT id FROM oms_cart_item WHERE member_id=? AND product_id=? AND zone_code=?"
                + " AND delete_status=0" + (cartItem.getProductSkuId() != null ? " AND product_sku_id=?" : "")
                + " LIMIT 1";
        List<Long> ids = cartItem.getProductSkuId() != null
                ? jdbc.queryForList(sql, Long.class, cartItem.getMemberId(), cartItem.getProductId(),
                        zoneCode, cartItem.getProductSkuId())
                : jdbc.queryForList(sql, Long.class, cartItem.getMemberId(), cartItem.getProductId(), zoneCode);
        if (ids.isEmpty()) {
            return null;
        }
        return cartItemMapper.selectByPrimaryKey(ids.get(0));
    }

    private String storedZone(Long cartItemId) {
        List<String> zones = jdbc.queryForList("SELECT zone_code FROM oms_cart_item WHERE id=?",
                String.class, cartItemId);
        return zones.isEmpty() ? null : zones.get(0);
    }

    /**
     * 解析加入购物车后生效的专区：显式专区必须与商品归属一致，否则按商品当前归属推断。
     * 商品只属于复购区或消费区时不能按自选区规则下单，避免越界使用其他专区结算方式。
     */
    private String resolveCartZone(Long productId, String requestedZone) {
        List<String> zones = productZones(productId);
        String requested = normalizeZone(requestedZone);
        if (requested != null) {
            if (!zones.contains(requested)) {
                Asserts.fail("该商品不在" + ZoneRules.label(requested) + "，不能加入该专区的购物车");
            }
            return requested;
        }
        return fallbackZone(zones);
    }

    /** 商品归属变化后，购物车行回落到的专区。 */
    private String correctedZone(Long productId, String storedZone) {
        List<String> zones = productZones(productId);
        String stored = normalizeZone(storedZone);
        if (stored != null && zones.contains(stored)) {
            return stored;
        }
        return fallbackZone(zones);
    }

    private String normalizeZone(String zoneCode) {
        if (zoneCode == null || zoneCode.isBlank()) {
            return null;
        }
        return zoneCode.trim().toUpperCase();
    }

    private String fallbackZone(List<String> zones) {
        if (zones.contains(ZoneRules.PICK)) {
            return ZoneRules.PICK;
        }
        if (zones.size() == 1) {
            return zones.get(0);
        }
        if (zones.isEmpty()) {
            return ZoneRules.NONE;
        }
        Asserts.fail("该商品同时属于复购区与消费区，请从对应专区页面加入购物车");
        return ZoneRules.NONE;
    }

    private List<String> productZones(Long productId) {
        if (productId == null) {
            return new ArrayList<>();
        }
        return jdbc.queryForList("SELECT zone_code FROM pms_product_zone WHERE product_id=?",
                String.class, productId);
    }

    private Map<Long, String> storedZones(Long memberId) {
        Map<Long, String> zones = new HashMap<>();
        jdbc.query("SELECT id,zone_code FROM oms_cart_item WHERE member_id=? AND delete_status=0",
                rs -> {
                    zones.put(rs.getLong("id"), rs.getString("zone_code"));
                }, memberId);
        return zones;
    }

    @Override
    public List<ZoneCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        Map<Long, String> storedZones = storedZones(memberId);
        List<ZoneCartItem> zoneItemList = new ArrayList<>();
        for (OmsCartItem item : cartItemList) {
            // 后台改价/改名/换图后购物车展示必须跟着总控变，不能一直显示加入时的旧快照
            refreshDisplaySnapshot(item);
            String stored = storedZones.get(item.getId());
            String zone = correctedZone(item.getProductId(), stored);
            if (!zone.equals(stored)) {
                jdbc.update("UPDATE oms_cart_item SET zone_code=? WHERE id=?", zone, item.getId());
            }
            ZoneCartItem zoneItem = new ZoneCartItem();
            BeanUtil.copyProperties(item, zoneItem);
            zoneItem.setZoneCode(zone);
            zoneItem.setZoneLabel(ZoneRules.label(zone));
            zoneItem.setZoneRule(ZoneRules.ruleLabel(zone));
            zoneItemList.add(zoneItem);
        }
        return zoneItemList;
    }

    /**
     * 只刷新展示字段（价格/名称/图片等），不做上下架与库存校验，失效商品仍可留在购物车里提示会员。
     */
    private void refreshDisplaySnapshot(OmsCartItem item) {
        if (item.getProductId() == null) {
            return;
        }
        PmsProduct product = productMapper.selectByPrimaryKey(item.getProductId());
        if (product == null) {
            return;
        }
        PmsSkuStock sku = item.getProductSkuId() == null ? null : skuStockMapper.selectByPrimaryKey(item.getProductSkuId());
        if (sku != null && sku.getPrice() != null) {
            item.setPrice(sku.getPrice());
        } else if (product.getPrice() != null) {
            item.setPrice(product.getPrice());
        }
        item.setProductName(product.getName());
        item.setProductPic(product.getPic());
        item.setProductSubTitle(product.getSubTitle());
        item.setProductBrand(product.getBrandName());
        item.setProductCategoryId(product.getProductCategoryId());
        if (sku != null) {
            item.setProductSkuCode(sku.getSkuCode());
            item.setProductAttr(sku.getSpData());
        }
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<ZoneCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        // 下单金额始终取当前商品/SKU 数据，不能信任历史购物车快照或客户端价格。
        for (ZoneCartItem item : cartItemList) {
            refreshFromCatalog(item);
            ensureStock(item.getProductSkuId(), item.getQuantity());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            // 促销计算结果沿专区字段复制，结算时按专区隔离计算
            cartPromotionItemList = promotionService.calcCartPromotion(new ArrayList<>(cartItemList));
        }
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            Asserts.fail("商品数量无效");
        }
        OmsCartItem owned = cartItemMapper.selectByPrimaryKey(id);
        if (owned == null || !memberId.equals(owned.getMemberId()) || !Integer.valueOf(0).equals(owned.getDeleteStatus())) {
            Asserts.fail("购物车商品不存在或无权操作");
        }
        ensureStock(owned.getProductSkuId(), quantity);
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(cartItem, example);
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdIn(ids).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    @Transactional
    public int updateAttr(OmsCartItem cartItem, String zoneCode) {
        UmsMember member = memberService.getCurrentMember();
        OmsCartItem owned = cartItem.getId() == null ? null : cartItemMapper.selectByPrimaryKey(cartItem.getId());
        if (owned == null || !member.getId().equals(owned.getMemberId()) || !Integer.valueOf(0).equals(owned.getDeleteStatus())) {
            Asserts.fail("购物车商品不存在或无权操作");
        }
        if (!owned.getProductId().equals(cartItem.getProductId())) {
            Asserts.fail("不能跨商品变更规格");
        }
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);
        cartItem.setId(null);
        // 重选规格后仍然保持在原专区，专区规则不变
        add(cartItem, zoneCode != null ? zoneCode : storedZone(owned.getId()));
        return 1;
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record,example);
    }

    private void refreshFromCatalog(OmsCartItem item) {
        if (item.getProductId() == null || item.getProductSkuId() == null) {
            Asserts.fail("商品或规格不能为空");
        }
        PmsProduct product = productMapper.selectByPrimaryKey(item.getProductId());
        PmsSkuStock sku = skuStockMapper.selectByPrimaryKey(item.getProductSkuId());
        if (product == null || sku == null || !item.getProductId().equals(sku.getProductId())
                || !Integer.valueOf(1).equals(product.getPublishStatus())
                || !Integer.valueOf(0).equals(product.getDeleteStatus())) {
            Asserts.fail("商品已下架或规格无效");
        }
        ensureStock(item.getProductSkuId(), item.getQuantity());
        item.setPrice(sku.getPrice() == null ? product.getPrice() : sku.getPrice());
        item.setProductName(product.getName());
        item.setProductPic(product.getPic());
        item.setProductSubTitle(product.getSubTitle());
        item.setProductSn(product.getProductSn());
        item.setProductBrand(product.getBrandName());
        item.setProductCategoryId(product.getProductCategoryId());
        item.setProductSkuCode(sku.getSkuCode());
        item.setProductAttr(sku.getSpData());
    }

    private void ensureStock(Long skuId, Integer quantity) {
        PmsSkuStock sku = skuId == null ? null : skuStockMapper.selectByPrimaryKey(skuId);
        if (sku == null || quantity == null || quantity < 1) {
            Asserts.fail("商品库存不足");
        }
        // 库存列允许为 NULL，历史脏数据还可能出现负的锁定库存，统一按 0 兜底
        int stock = sku.getStock() == null ? 0 : sku.getStock();
        int lockStock = sku.getLockStock() == null ? 0 : sku.getLockStock();
        if (stock - Math.max(lockStock, 0) < quantity) {
            Asserts.fail("商品库存不足");
        }
    }
}
