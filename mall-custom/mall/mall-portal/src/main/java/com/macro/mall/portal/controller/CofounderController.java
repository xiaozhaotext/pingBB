package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.BizCofounderConfig;
import com.macro.mall.portal.domain.BizCofounderOrder;
import com.macro.mall.portal.service.CofounderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 联创权益（模拟）- 消费者端
 * 注意：所有支付与分红均为模拟，前端须明确标注"模拟"，不接入真实资金。
 */
@RestController
@Tag(name = "CofounderController", description = "联创权益(模拟)")
@RequestMapping("/member/cofounder")
public class CofounderController {

    @Autowired
    private CofounderService cofounderService;

    @Operation(summary = "获取联创权益方案(模拟)")
    @GetMapping("/config")
    public CommonResult<BizCofounderConfig> config() {
        return CommonResult.success(cofounderService.getConfig());
    }

    @Operation(summary = "创建模拟购买订单(幂等键bizId)")
    @PostMapping("/order")
    public CommonResult<BizCofounderOrder> order(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }

    @Operation(summary = "模拟支付并升级为联创(事务+幂等)")
    @PostMapping("/pay")
    public CommonResult<Void> pay(@RequestBody Map<String, Object> body) {
        return CommonResult.forbidden(null);
    }
}
