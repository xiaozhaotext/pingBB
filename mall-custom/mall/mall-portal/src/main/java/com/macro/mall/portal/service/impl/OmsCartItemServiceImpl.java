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
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    @Override
    public int add(OmsCartItem cartItem) {
        int count;
        if (cartItem.getQuantity() == null || cartItem.getQuantity() < 1 || cartItem.getQuantity() > 99) {
            Asserts.fail("商品数量无效");
        }
        refreshFromCatalog(cartItem);
        UmsMember currentMember =memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        OmsCartItem existCartItem = getCartItem(cartItem);
        if (existCartItem == null) {
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
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
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        if (cartItem.getProductSkuId()!=null) {
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        // 后台改价/改名/换图后购物车展示必须跟着总控变，不能一直显示加入时的旧快照
        for (OmsCartItem item : cartItemList) {
            refreshDisplaySnapshot(item);
        }
        return cartItemList;
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
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        // 下单金额始终取当前商品/SKU 数据，不能信任历史购物车快照或客户端价格。
        for (OmsCartItem item : cartItemList) {
            refreshFromCatalog(item);
            ensureStock(item.getProductSkuId(), item.getQuantity());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
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
    public int updateAttr(OmsCartItem cartItem) {
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
        add(cartItem);
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
