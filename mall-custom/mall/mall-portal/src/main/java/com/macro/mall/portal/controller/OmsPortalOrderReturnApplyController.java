package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.service.OmsPortalOrderReturnApplyService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退货申请管理Controller
 * Created by macro on 2018/10/17.
 */
@Controller
@Tag(name = "OmsPortalOrderReturnApplyController",description = "退货申请管理")
@RequestMapping("/returnApply")
public class OmsPortalOrderReturnApplyController {
    @Autowired
    private OmsPortalOrderReturnApplyService returnApplyService;
    @Autowired
    private UmsMemberService memberService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "申请退货")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult create(@RequestBody OmsOrderReturnApplyParam returnApply) {
        int count = returnApplyService.create(returnApply);
        if (count > 0) {
            return CommonResult.success(count);
        }
        return CommonResult.failed();
    }

    @Operation(summary = "查询我的退款/售后申请")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        UmsMember member = memberService.getCurrentMember();
        int limit = Math.min(Math.max(pageSize == null ? 10 : pageSize, 1), 50);
        int offset = (Math.max(pageNum == null ? 1 : pageNum, 1) - 1) * limit;
        String username = member.getUsername();
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id,order_id,order_sn,product_id,product_name,product_pic,product_attr,product_count,"
                        + "product_price,product_real_price,return_amount,reason,description,status,create_time,handle_time "
                        + "FROM oms_order_return_apply WHERE member_username = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                username, limit, offset);
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oms_order_return_apply WHERE member_username = ?", Integer.class, username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", items);
        data.put("total", total == null ? 0 : total);
        data.put("pageNum", Math.max(pageNum == null ? 1 : pageNum, 1));
        data.put("pageSize", limit);
        return CommonResult.success(data);
    }
}
