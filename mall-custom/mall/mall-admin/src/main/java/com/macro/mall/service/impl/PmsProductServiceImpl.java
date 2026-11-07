package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.*;
import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.dto.PmsProductResult;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.service.PmsProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class PmsProductServiceImpl implements PmsProductService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PmsProductServiceImpl.class);
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsMemberPriceDao memberPriceDao;
    @Autowired
    private PmsMemberPriceMapper memberPriceMapper;
    @Autowired
    private PmsProductLadderDao productLadderDao;
    @Autowired
    private PmsProductLadderMapper productLadderMapper;
    @Autowired
    private PmsProductFullReductionDao productFullReductionDao;
    @Autowired
    private PmsProductFullReductionMapper productFullReductionMapper;
    @Autowired
    private PmsSkuStockDao skuStockDao;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private PmsProductAttributeValueDao productAttributeValueDao;
    @Autowired
    private PmsProductAttributeValueMapper productAttributeValueMapper;
    @Autowired
    private CmsSubjectProductRelationDao subjectProductRelationDao;
    @Autowired
    private CmsSubjectProductRelationMapper subjectProductRelationMapper;
    @Autowired
    private CmsPrefrenceAreaProductRelationDao prefrenceAreaProductRelationDao;
    @Autowired
    private CmsPrefrenceAreaProductRelationMapper prefrenceAreaProductRelationMapper;
    @Autowired
    private PmsProductDao productDao;
    @Autowired
    private PmsProductVertifyRecordDao productVertifyRecordDao;
    @Autowired
    private JdbcTemplate jdbc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int create(PmsProductParam productParam) {
        int count;
        //先校验：少了什么、哪里不合规，一次性告诉前端，避免只返回一个 400
        validateProductParam(productParam, true);
        //创建商品
        PmsProduct product = productParam;
        product.setId(null);
        productMapper.insertSelective(product);
        //根据促销类型设置价格：会员价格、阶梯价格、满减价格
        Long productId = product.getId();
        //SKU 价格/库存兜底；未设置规格时补一条默认 SKU，保证上架即可下单
        productParam.setSkuStockList(prepareSkuStockList(product, productParam.getSkuStockList()));
        //会员价格
        relateAndInsertList(memberPriceDao, productParam.getMemberPriceList(), productId);
        //阶梯价格
        relateAndInsertList(productLadderDao, productParam.getProductLadderList(), productId);
        //满减价格
        relateAndInsertList(productFullReductionDao, productParam.getProductFullReductionList(), productId);
        //处理sku的编码
        handleSkuStockCode(productParam.getSkuStockList(),productId);
        //添加sku库存信息
        relateAndInsertList(skuStockDao, productParam.getSkuStockList(), productId);
        //添加商品参数,添加自定义商品规格
        relateAndInsertList(productAttributeValueDao, productParam.getProductAttributeValueList(), productId);
        //关联专题
        relateAndInsertList(subjectProductRelationDao, productParam.getSubjectProductRelationList(), productId);
        //关联优选
        relateAndInsertList(prefrenceAreaProductRelationDao, productParam.getPrefrenceAreaProductRelationList(), productId);
        saveZone(productId, productParam.getZoneCode());
        count = 1;
        return count;
    }

    private void handleSkuStockCode(List<PmsSkuStock> skuStockList, Long productId) {
        if(CollectionUtils.isEmpty(skuStockList))return;
        for(int i=0;i<skuStockList.size();i++){
            PmsSkuStock skuStock = skuStockList.get(i);
            if(StrUtil.isEmpty(skuStock.getSkuCode())){
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                StringBuilder sb = new StringBuilder();
                //日期
                sb.append(sdf.format(new Date()));
                //四位商品id
                sb.append(String.format("%04d", productId));
                //三位索引id
                sb.append(String.format("%03d", i+1));
                skuStock.setSkuCode(sb.toString());
            }
        }
    }

    @Override
    public PmsProductResult getUpdateInfo(Long id) {
        return productDao.getUpdateInfo(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, PmsProductParam productParam) {
        int count;
        validateProductParam(productParam, false);
        //更新商品信息
        PmsProduct product = productParam;
        product.setId(id);
        PmsProduct originProduct = productMapper.selectByPrimaryKey(id);
        //改商品价格时同步 SKU 价格，否则应用端详情页/购物车一直显示旧价
        syncSkuPriceWithProduct(originProduct, product, productParam.getSkuStockList());
        //SKU 价格/库存兜底；未设置规格时补一条默认 SKU，保证上架即可下单
        productParam.setSkuStockList(prepareSkuStockList(product, productParam.getSkuStockList()));
        productMapper.updateByPrimaryKeySelective(product);
        if (productParam.getBrandId() == null) {
            jdbc.update("UPDATE pms_product SET brand_id=NULL, brand_name=NULL WHERE id=?", id);
        }
        //会员价格
        PmsMemberPriceExample pmsMemberPriceExample = new PmsMemberPriceExample();
        pmsMemberPriceExample.createCriteria().andProductIdEqualTo(id);
        memberPriceMapper.deleteByExample(pmsMemberPriceExample);
        relateAndInsertList(memberPriceDao, productParam.getMemberPriceList(), id);
        //阶梯价格
        PmsProductLadderExample ladderExample = new PmsProductLadderExample();
        ladderExample.createCriteria().andProductIdEqualTo(id);
        productLadderMapper.deleteByExample(ladderExample);
        relateAndInsertList(productLadderDao, productParam.getProductLadderList(), id);
        //满减价格
        PmsProductFullReductionExample fullReductionExample = new PmsProductFullReductionExample();
        fullReductionExample.createCriteria().andProductIdEqualTo(id);
        productFullReductionMapper.deleteByExample(fullReductionExample);
        relateAndInsertList(productFullReductionDao, productParam.getProductFullReductionList(), id);
        //修改sku库存信息
        handleUpdateSkuStockList(id, productParam);
        //修改商品参数,添加自定义商品规格
        PmsProductAttributeValueExample productAttributeValueExample = new PmsProductAttributeValueExample();
        productAttributeValueExample.createCriteria().andProductIdEqualTo(id);
        productAttributeValueMapper.deleteByExample(productAttributeValueExample);
        relateAndInsertList(productAttributeValueDao, productParam.getProductAttributeValueList(), id);
        //关联专题
        CmsSubjectProductRelationExample subjectProductRelationExample = new CmsSubjectProductRelationExample();
        subjectProductRelationExample.createCriteria().andProductIdEqualTo(id);
        subjectProductRelationMapper.deleteByExample(subjectProductRelationExample);
        relateAndInsertList(subjectProductRelationDao, productParam.getSubjectProductRelationList(), id);
        //关联优选
        CmsPrefrenceAreaProductRelationExample prefrenceAreaExample = new CmsPrefrenceAreaProductRelationExample();
        prefrenceAreaExample.createCriteria().andProductIdEqualTo(id);
        prefrenceAreaProductRelationMapper.deleteByExample(prefrenceAreaExample);
        relateAndInsertList(prefrenceAreaProductRelationDao, productParam.getPrefrenceAreaProductRelationList(), id);
        saveZone(id, productParam.getZoneCode());
        count = 1;
        return count;
    }

    /**
     * 保存前把能判断出来的问题一次性列清楚：少了哪个必填项、哪个值不合规。
     * creating=true 时校验必填，false 时只校验已填的内容（兼容局部更新）。
     */
    private void validateProductParam(PmsProductParam productParam, boolean creating) {
        if (productParam == null) throw bad("没有收到商品数据：请求体是空的，请刷新页面重新填写");
        List<String> problems = new ArrayList<>();
        String zone = productParam.getZoneCode();
        if (StrUtil.isNotBlank(zone) && !List.of("PICK", "REBUY", "CONSUME").contains(zone))
            problems.add("商品专区「" + zone + "」不存在，只能是 PICK(自选区)、REBUY(复购区)、CONSUME(消费区)");
        String name = productParam.getName();
        if (creating && StrUtil.isBlank(name)) problems.add("缺少「商品名称」");
        else if (name != null && name.trim().length() > 200) problems.add("「商品名称」超过 200 字，请精简");
        if (creating && productParam.getProductCategoryId() == null) problems.add("缺少「商品分类」，请先选择分类");
        if ("PICK".equals(zone)) {
            if (productParam.getProductCategoryId() != null) {
                String categoryName = categoryName(productParam.getProductCategoryId());
                if (categoryName == null)
                    problems.add("商品分类 id=" + productParam.getProductCategoryId() + " 已经不存在（可能被删除），请重新选择「旅游路线」");
                else if (!"旅游路线".equals(categoryName))
                    problems.add("自选区只能选「旅游路线」分类，当前选的是「" + categoryName + "」");
            }
            BigDecimal price = productParam.getPrice();
            if (price != null && price.compareTo(new BigDecimal("3999.00")) != 0)
                problems.add("自选区商品售价固定 3999 元，当前填的是 " + price.toPlainString() + " 元");
            List<PmsSkuStock> skus = productParam.getSkuStockList();
            if (skus != null) {
                for (int i = 0; i < skus.size(); i++) {
                    PmsSkuStock sku = skus.get(i);
                    if (sku == null) continue;
                    if (sku.getPrice() == null || sku.getPrice().compareTo(new BigDecimal("3999.00")) != 0)
                        problems.add("第 " + (i + 1) + " 个规格" + (StrUtil.isBlank(sku.getSkuCode()) ? "" : "（" + sku.getSkuCode() + "）")
                                + "价格是 " + (sku.getPrice() == null ? "空" : sku.getPrice().toPlainString() + " 元")
                                + "，自选区每个规格都必须是 3999 元");
                }
            }
        }
        if (!problems.isEmpty())
            throw bad("商品数据有问题，没能保存：\n" + String.join("\n", problems.stream().map(p -> "· " + p).toList()));
    }

    /** 按库里已保存的数据检查自选区规则，返回逐条问题（空表示合规）。 */
    private List<String> pickProblems(Long productId) {
        List<String> problems = new ArrayList<>();
        List<String> names = jdbc.queryForList("SELECT c.name FROM pms_product p "
                + "JOIN pms_product_category c ON c.id=p.product_category_id WHERE p.id=?", String.class, productId);
        String categoryName = names.isEmpty() ? null : names.get(0);
        if (categoryName == null) problems.add("查不到这个商品的分类，请重新选择「旅游路线」");
        else if (!"旅游路线".equals(categoryName)) problems.add("商品分类是「" + categoryName + "」，自选区必须是「旅游路线」");
        List<BigDecimal> prices = jdbc.queryForList("SELECT price FROM pms_product WHERE id=?", BigDecimal.class, productId);
        BigDecimal price = prices.isEmpty() ? null : prices.get(0);
        if (price == null || price.compareTo(new BigDecimal("3999.00")) != 0)
            problems.add("商品售价是 " + (price == null ? "空" : price.toPlainString() + " 元") + "，自选区必须是 3999 元");
        List<java.util.Map<String, Object>> skus = jdbc.queryForList("SELECT id,sku_code,price FROM pms_sku_stock WHERE product_id=?", productId);
        if (skus.isEmpty()) problems.add("这个商品还没有任何规格，请至少添加一条 3999 元的规格");
        for (java.util.Map<String, Object> sku : skus) {
            Object skuPrice = sku.get("price");
            Object code = sku.get("sku_code");
            boolean priced = skuPrice instanceof BigDecimal decimal && decimal.compareTo(new BigDecimal("3999.00")) == 0;
            if (!priced) {
                String label = code == null || String.valueOf(code).isBlank() ? "id=" + sku.get("id") : String.valueOf(code);
                problems.add("规格「" + label + "」价格是 " + (skuPrice == null ? "空" : String.valueOf(skuPrice) + " 元") + "，必须是 3999 元");
            }
        }
        return problems;
    }

    private String categoryName(Long categoryId) {
        List<String> names = jdbc.queryForList("SELECT name FROM pms_product_category WHERE id=?", String.class, categoryId);
        return names.isEmpty() ? null : names.get(0);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private void saveZone(Long productId, String zone) {
        // Legacy callers that do not edit a zone retain their existing assignment.
        if (zone == null) return;
        if (!List.of("PICK", "REBUY", "CONSUME").contains(zone))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品专区无效");
        if ("PICK".equals(zone)) {
            List<String> problems = pickProblems(productId);
            if (!problems.isEmpty())
                throw bad("自选区商品还不符合规则，没能保存：\n" + String.join("\n", problems.stream().map(p -> "· " + p).toList()));
            String adminName = SecurityContextHolder.getContext().getAuthentication().getName();
            Long operatorId = jdbc.queryForObject("SELECT id FROM ums_admin WHERE username=?", Long.class, adminName);
            jdbc.update("INSERT INTO tri_membership_product(product_id,enabled,updated_by) VALUES(?,1,?) "
                    + "ON DUPLICATE KEY UPDATE enabled=1,updated_by=VALUES(updated_by)", productId, operatorId);
        } else {
            jdbc.update("UPDATE tri_membership_product SET enabled=0 WHERE product_id=?", productId);
        }
        jdbc.update("DELETE FROM pms_product_zone WHERE product_id=? AND zone_code<>?", productId, zone);
        jdbc.update("INSERT INTO pms_product_zone(product_id,zone_code,sort_order) "
                + "SELECT id,?,COALESCE(sort,0) FROM pms_product WHERE id=? "
                + "ON DUPLICATE KEY UPDATE sort_order=VALUES(sort_order)", zone, productId);
    }

    private void handleUpdateSkuStockList(Long id, PmsProductParam productParam) {
        //当前的sku信息
        List<PmsSkuStock> currSkuList = productParam.getSkuStockList();
        //当前没有sku直接删除
        if(CollUtil.isEmpty(currSkuList)){
            PmsSkuStockExample skuStockExample = new PmsSkuStockExample();
            skuStockExample.createCriteria().andProductIdEqualTo(id);
            skuStockMapper.deleteByExample(skuStockExample);
            return;
        }
        //获取初始sku信息
        PmsSkuStockExample skuStockExample = new PmsSkuStockExample();
        skuStockExample.createCriteria().andProductIdEqualTo(id);
        List<PmsSkuStock> oriStuList = skuStockMapper.selectByExample(skuStockExample);
        //获取新增sku信息
        List<PmsSkuStock> insertSkuList = currSkuList.stream().filter(item->item.getId()==null).collect(Collectors.toList());
        //获取需要更新的sku信息
        List<PmsSkuStock> updateSkuList = currSkuList.stream().filter(item->item.getId()!=null).collect(Collectors.toList());
        List<Long> updateSkuIds = updateSkuList.stream().map(PmsSkuStock::getId).collect(Collectors.toList());
        //获取需要删除的sku信息
        List<PmsSkuStock> removeSkuList = oriStuList.stream().filter(item-> !updateSkuIds.contains(item.getId())).collect(Collectors.toList());
        handleSkuStockCode(insertSkuList,id);
        handleSkuStockCode(updateSkuList,id);
        //新增sku
        if(CollUtil.isNotEmpty(insertSkuList)){
            relateAndInsertList(skuStockDao, insertSkuList, id);
        }
        //删除sku
        if(CollUtil.isNotEmpty(removeSkuList)){
            List<Long> removeSkuIds = removeSkuList.stream().map(PmsSkuStock::getId).collect(Collectors.toList());
            PmsSkuStockExample removeExample = new PmsSkuStockExample();
            removeExample.createCriteria().andIdIn(removeSkuIds);
            skuStockMapper.deleteByExample(removeExample);
        }
        //修改sku
        if(CollUtil.isNotEmpty(updateSkuList)){
            for (PmsSkuStock pmsSkuStock : updateSkuList) {
                skuStockMapper.updateByPrimaryKeySelective(pmsSkuStock);
            }
        }

    }

    /**
     * SKU 兜底：后台“商品属性”步骤生成的 SKU 只有销售属性，价格、库存、锁定库存均为空，
     * 会导致应用端详情页无价、加购与下单提示“库存不足”。这里统一用商品维度的值补齐；
     * 商品未设置任何规格（SKU 列表为空）且已上架时补一条默认 SKU，保证上架即可下单。
     */
    private List<PmsSkuStock> prepareSkuStockList(PmsProduct product, List<PmsSkuStock> skuStockList) {
        List<PmsSkuStock> result = skuStockList == null ? new ArrayList<>() : skuStockList;
        int productStock = product.getStock() == null ? 0 : product.getStock();
        int productLowStock = product.getLowStock() == null ? 0 : product.getLowStock();
        if (CollUtil.isEmpty(result)) {
            if (!Integer.valueOf(1).equals(product.getPublishStatus())) {
                return result;
            }
            PmsSkuStock defaultSku = new PmsSkuStock();
            defaultSku.setPrice(product.getPrice());
            defaultSku.setStock(productStock);
            defaultSku.setLowStock(productLowStock);
            defaultSku.setLockStock(0);
            // 空规格数组：前台 initSpecList/getSkuStock 会直接命中这条默认 SKU
            defaultSku.setSpData("[]");
            result.add(defaultSku);
            return result;
        }
        for (PmsSkuStock skuStock : result) {
            if (skuStock.getPrice() == null) {
                skuStock.setPrice(product.getPrice());
            }
            if (skuStock.getStock() == null) {
                skuStock.setStock(productStock);
            }
            if (skuStock.getLowStock() == null) {
                skuStock.setLowStock(productLowStock);
            }
            if (skuStock.getLockStock() == null) {
                skuStock.setLockStock(0);
            }
            if (StrUtil.isEmpty(skuStock.getSpData())) {
                skuStock.setSpData("[]");
            }
        }
        return result;
    }

    /**
     * 商品价格变化时同步 SKU 价格：应用端首页/列表读的是 pms_product.price，而详情页与购物车
     * 读的是 pms_sku_stock.price，只改商品价会让两处长期不一致（改完价首页变了、详情与购物车没变）。
     * 因此运营在后台改了“商品价格”就视为该商品所有规格的新售价，统一跟随；
     * 需要按规格单独定价时，请在商品属性步骤维护 SKU 价格并保持与商品价一致（可用批量同步按钮）。
     */
    private void syncSkuPriceWithProduct(PmsProduct originProduct, PmsProduct newProduct, List<PmsSkuStock> skuStockList) {
        if (originProduct == null || newProduct == null || newProduct.getPrice() == null || CollUtil.isEmpty(skuStockList)) {
            return;
        }
        BigDecimal originPrice = originProduct.getPrice();
        BigDecimal newPrice = newProduct.getPrice();
        if (originPrice != null && originPrice.compareTo(newPrice) == 0) {
            return;
        }
        for (PmsSkuStock skuStock : skuStockList) {
            skuStock.setPrice(newPrice);
        }
    }

    @Override
    public List<PmsProduct> list(PmsProductQueryParam productQueryParam, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductExample productExample = new PmsProductExample();
        PmsProductExample.Criteria criteria = productExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        if (productQueryParam.getPublishStatus() != null) {
            criteria.andPublishStatusEqualTo(productQueryParam.getPublishStatus());
        }
        if (productQueryParam.getVerifyStatus() != null) {
            criteria.andVerifyStatusEqualTo(productQueryParam.getVerifyStatus());
        }
        if (!StrUtil.isEmpty(productQueryParam.getKeyword())) {
            criteria.andNameLike("%" + productQueryParam.getKeyword() + "%");
        }
        if (!StrUtil.isEmpty(productQueryParam.getProductSn())) {
            criteria.andProductSnEqualTo(productQueryParam.getProductSn());
        }
        if (productQueryParam.getBrandId() != null) {
            criteria.andBrandIdEqualTo(productQueryParam.getBrandId());
        }
        if (productQueryParam.getProductCategoryId() != null) {
            criteria.andProductCategoryIdEqualTo(productQueryParam.getProductCategoryId());
        }
        return productMapper.selectByExample(productExample);
    }

    @Override
    public int updateVerifyStatus(List<Long> ids, Integer verifyStatus, String detail) {
        PmsProduct product = new PmsProduct();
        product.setVerifyStatus(verifyStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        List<PmsProductVertifyRecord> list = new ArrayList<>();
        int count = productMapper.updateByExampleSelective(product, example);
        //修改完审核状态后插入审核记录
        for (Long id : ids) {
            PmsProductVertifyRecord record = new PmsProductVertifyRecord();
            record.setProductId(id);
            record.setCreateTime(new Date());
            record.setDetail(detail);
            record.setStatus(verifyStatus);
            record.setVertifyMan("test");
            list.add(record);
        }
        productVertifyRecordDao.insertList(list);
        return count;
    }

    @Override
    public int updatePublishStatus(List<Long> ids, Integer publishStatus) {
        PmsProduct record = new PmsProduct();
        record.setPublishStatus(publishStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateRecommendStatus(List<Long> ids, Integer recommendStatus) {
        PmsProduct record = new PmsProduct();
        record.setRecommandStatus(recommendStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateNewStatus(List<Long> ids, Integer newStatus) {
        PmsProduct record = new PmsProduct();
        record.setNewStatus(newStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public int updateDeleteStatus(List<Long> ids, Integer deleteStatus) {
        PmsProduct record = new PmsProduct();
        record.setDeleteStatus(deleteStatus);
        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andIdIn(ids);
        return productMapper.updateByExampleSelective(record, example);
    }

    @Override
    public List<PmsProduct> list(String keyword) {
        PmsProductExample productExample = new PmsProductExample();
        PmsProductExample.Criteria criteria = productExample.createCriteria();
        criteria.andDeleteStatusEqualTo(0);
        if(!StrUtil.isEmpty(keyword)){
            criteria.andNameLike("%" + keyword + "%");
            productExample.or().andDeleteStatusEqualTo(0).andProductSnLike("%" + keyword + "%");
        }
        return productMapper.selectByExample(productExample);
    }

    /**
     * 建立和插入关系表操作
     *
     * @param dao       可以操作的dao
     * @param dataList  要插入的数据
     * @param productId 建立关系的id
     */
    private void relateAndInsertList(Object dao, List dataList, Long productId) {
        try {
            if (CollectionUtils.isEmpty(dataList)) return;
            for (Object item : dataList) {
                Method setId = item.getClass().getMethod("setId", Long.class);
                setId.invoke(item, (Long) null);
                Method setProductId = item.getClass().getMethod("setProductId", Long.class);
                setProductId.invoke(item, productId);
            }
            Method insertList = dao.getClass().getMethod("insertList", List.class);
            insertList.invoke(dao, dataList);
        } catch (Exception e) {
            LOGGER.warn("创建产品出错:{}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

}
