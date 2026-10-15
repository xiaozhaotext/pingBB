package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.PmsPortalProductDetail;
import com.macro.mall.portal.domain.PmsProductCategoryNode;
import com.macro.mall.portal.service.PmsPortalProductService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 前台商品管理Controller
 * Created by macro on 2020/4/6.
 */
@Controller
@Tag(name = "PmsPortalProductController", description = "前台商品管理")
@RequestMapping("/product")
public class PmsPortalProductController {

    @Autowired
    private PmsPortalProductService portalProductService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "综合搜索、筛选、排序")
    @Parameter(name = "sort", description = "排序字段:0->按相关度；1->按新品；2->按销量；3->价格从低到高；4->价格从高到低",
            in= ParameterIn.QUERY,schema = @Schema(type = "integer",defaultValue = "0",allowableValues = {"0","1","2","3","4"}))
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsProduct>> search(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long brandId,
                                                       @RequestParam(required = false) Long productCategoryId,
                                                       @RequestParam(required = false, defaultValue = "0") Integer pageNum,
                                                       @RequestParam(required = false, defaultValue = "5") Integer pageSize,
                                                       @RequestParam(required = false, defaultValue = "0") Integer sort) {
        List<PmsProduct> productList = portalProductService.search(keyword, brandId, productCategoryId, pageNum, pageSize, sort);
        return CommonResult.success(CommonPage.restPage(productList));
    }

    @Operation(summary = "以树形结构获取所有商品分类")
    @RequestMapping(value = "/categoryTreeList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsProductCategoryNode>> categoryTreeList() {
        List<PmsProductCategoryNode> list = portalProductService.categoryTreeList();
        return CommonResult.success(list);
    }

    @Operation(summary = "获取前台商品详情")
    @RequestMapping(value = "/detail/{id}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<PmsPortalProductDetail> detail(@PathVariable Long id) {
        PmsPortalProductDetail productDetail = portalProductService.detail(id);
        return CommonResult.success(productDetail);
    }

    @Operation(summary = "分页查询商品评价（含商家回复）")
    @RequestMapping(value = "/comment/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> commentList(@RequestParam Long productId,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        int limit = Math.min(Math.max(pageSize == null ? 10 : pageSize, 1), 50);
        int page = Math.max(pageNum == null ? 1 : pageNum, 1);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id,member_nick_name AS memberNickName,member_icon AS memberIcon,star,content,pics,"
                        + "product_attribute AS productAttribute,create_time AS createTime,replay_count AS replayCount "
                        + "FROM pms_comment WHERE product_id=? AND show_status=1 ORDER BY id DESC LIMIT ? OFFSET ?",
                productId, limit, (page - 1) * limit);
        if (!list.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> row : list) {
                Object value = row.get("id");
                if (value instanceof Number) {
                    ids.add(((Number) value).longValue());
                }
            }
            if (!ids.isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
                List<Map<String, Object>> replays = jdbcTemplate.queryForList(
                        "SELECT id,comment_id AS commentId,member_nick_name AS memberNickName,member_icon AS memberIcon,"
                                + "content,type,create_time AS createTime FROM pms_comment_replay WHERE comment_id IN ("
                                + placeholders + ") ORDER BY id", ids.toArray());
                Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
                for (Map<String, Object> replay : replays) {
                    Object commentId = replay.get("commentId");
                    if (commentId instanceof Number) {
                        grouped.computeIfAbsent(((Number) commentId).longValue(), key -> new ArrayList<>()).add(replay);
                    }
                }
                for (Map<String, Object> row : list) {
                    Object value = row.get("id");
                    if (value instanceof Number) {
                        row.put("replays", grouped.getOrDefault(((Number) value).longValue(), Collections.emptyList()));
                    }
                }
            }
        }
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pms_comment WHERE product_id=? AND show_status=1", Integer.class, productId);
        Double avgStar = jdbcTemplate.queryForObject(
                "SELECT AVG(star) FROM pms_comment WHERE product_id=? AND show_status=1", Double.class, productId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total == null ? 0 : total);
        data.put("pageNum", page);
        data.put("pageSize", limit);
        data.put("avgStar", avgStar == null ? null : Math.round(avgStar * 10) / 10.0);
        return CommonResult.success(data);
    }

    @Operation(summary = "发表商品评价（需订单已完成）")
    @RequestMapping(value = "/comment/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Object> commentCreate(@RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") == null ? null : Long.valueOf(String.valueOf(body.get("productId")));
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId")));
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();
        int star = body.get("star") == null ? 5 : Integer.parseInt(String.valueOf(body.get("star")));
        if (productId == null || orderId == null) {
            return CommonResult.failed("商品与订单不能为空");
        }
        if (content.isEmpty() || content.length() > 500) {
            return CommonResult.failed("评价内容需为 1-500 字");
        }
        if (star < 1 || star > 5) {
            star = 5;
        }
        UmsMember member = memberService.getCurrentMember();
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT id FROM oms_order WHERE id=? AND member_id=? AND status=3", orderId, member.getId());
        if (orders.isEmpty()) {
            return CommonResult.failed("订单不存在或尚未完成，暂不能评价");
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT product_name AS productName,product_attr AS productAttr FROM oms_order_item WHERE order_id=? AND product_id=?",
                orderId, productId);
        if (items.isEmpty()) {
            return CommonResult.failed("该订单不包含此商品");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pms_comment WHERE product_id=? AND member_id=? AND order_id=?",
                Integer.class, productId, member.getId(), orderId);
        if (exists != null && exists > 0) {
            return CommonResult.failed("该订单已评价，请勿重复提交");
        }
        String nickname = member.getNickname() == null ? member.getUsername() : member.getNickname();
        jdbcTemplate.update(
                "INSERT INTO pms_comment(product_id,member_id,order_id,member_nick_name,member_icon,product_name,"
                        + "product_attribute,star,content,create_time,show_status,replay_count) VALUES(?,?,?,?,?,?,?,?,?,NOW(),1,0)",
                productId, member.getId(), orderId, nickname, member.getIcon(),
                items.get(0).get("productName"), items.get(0).get("productAttr"), star, content);
        return CommonResult.success(null);
    }

    @Operation(summary = "查看当前会员可评价的订单商品")
    @RequestMapping(value = "/comment/pending", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<Map<String, Object>>> commentPending() {
        UmsMember member = memberService.getCurrentMember();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT oi.order_id AS orderId,oi.product_id AS productId,oi.product_name AS productName,"
                        + "oi.product_pic AS productPic,oi.product_attr AS productAttr,o.order_sn AS orderSn "
                        + "FROM oms_order o JOIN oms_order_item oi ON oi.order_id=o.id "
                        + "WHERE o.member_id=? AND o.status=3 AND NOT EXISTS (SELECT 1 FROM pms_comment c "
                        + "WHERE c.order_id=o.id AND c.product_id=oi.product_id AND c.member_id=o.member_id) "
                        + "ORDER BY o.id DESC LIMIT 50", member.getId());
        return CommonResult.success(rows);
    }
}
