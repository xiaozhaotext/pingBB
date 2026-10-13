package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductCategory;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 首页内容管理Controller
 * Created by macro on 2019/1/28.
 */
@Controller
@Tag(name = "HomeController", description = "首页内容管理")
@RequestMapping("/home")
public class HomeController {
    @Autowired
    private HomeService homeService;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @GetMapping("/announcements")
    @ResponseBody
    public CommonResult<java.util.List<java.util.Map<String,Object>>> announcements() {
        return CommonResult.success(jdbc.queryForList("SELECT id,title,content,publish_time FROM sys_notice WHERE status=1 AND type='SYSTEM' ORDER BY publish_time DESC,id DESC LIMIT 5"));
    }
    @Operation(summary = "站点公开信息：公司资料、水印与合规文本")
    @RequestMapping(value = "/siteInfo", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<java.util.Map<String, Object>> siteInfo() {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("company", configByPrefix("company."));
        data.put("watermark", configByPrefix("watermark."));
        data.put("contents", siteContents());
        return CommonResult.success(data);
    }

    /**
     * Public projection of one sys_config namespace. Only company.* and watermark.* are exposed;
     * payment, SMS and mini program credentials are deliberately never returned to the storefront.
     */
    private java.util.Map<String, Object> configByPrefix(String prefix) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        try {
            for (java.util.Map<String, Object> row : jdbc.queryForList(
                    "SELECT config_key,config_value FROM sys_config WHERE config_key LIKE ?", prefix + "%")) {
                String key = String.valueOf(row.get("config_key"));
                values.put(key.substring(prefix.length()), row.get("config_value"));
            }
        } catch (org.springframework.dao.DataAccessException e) {
            // Settings table not reachable: return an empty section instead of failing the whole page.
        }
        return values;
    }

    /** Compliance texts live in sys_content (created by 22_admin_settings_center.sql). */
    private java.util.Map<String, Object> siteContents() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        try {
            for (java.util.Map<String, Object> row : jdbc.queryForList(
                    "SELECT content_key,content_value FROM sys_content WHERE content_key IN ('privacy_policy','user_agreement')")) {
                values.put(String.valueOf(row.get("content_key")), row.get("content_value"));
            }
        } catch (org.springframework.dao.DataAccessException e) {
            // Content table not migrated yet.
        }
        return values;
    }

    @Operation(summary = "首页内容信息展示")
    @RequestMapping(value = "/content", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<HomeContentResult> content() {
        HomeContentResult contentResult = homeService.content();
        return CommonResult.success(contentResult);
    }

    @Operation(summary = "分页获取推荐商品")
    @RequestMapping(value = "/recommendProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> recommendProductList(@RequestParam(value = "pageSize", defaultValue = "4") Integer pageSize,
                                                               @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<PmsProduct> productList = homeService.recommendProductList(pageSize, pageNum);
        return CommonResult.success(productList);
    }

    @Operation(summary = "获取首页商品分类")
    @RequestMapping(value = "/productCateList/{parentId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProductCategory>> getProductCateList(@PathVariable Long parentId) {
        List<PmsProductCategory> productCategoryList = homeService.getProductCateList(parentId);
        return CommonResult.success(productCategoryList);
    }

    @Operation(summary = "根据分类获取专题")
    @RequestMapping(value = "/subjectList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<CmsSubject>> getSubjectList(@RequestParam(required = false) Long cateId,
                                                         @RequestParam(value = "pageSize", defaultValue = "4") Integer pageSize,
                                                         @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<CmsSubject> subjectList = homeService.getSubjectList(cateId,pageSize,pageNum);
        return CommonResult.success(subjectList);
    }

    @Operation(summary = "分页获取人气推荐商品")
    @RequestMapping(value = "/hotProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> hotProductList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                         @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        List<PmsProduct> productList = homeService.hotProductList(pageNum,pageSize);
        return CommonResult.success(productList);
    }

    @Operation(summary = "分页获取新品推荐商品")
    @RequestMapping(value = "/newProductList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProduct>> newProductList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                         @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        List<PmsProduct> productList = homeService.newProductList(pageNum,pageSize);
        return CommonResult.success(productList);
    }
}
