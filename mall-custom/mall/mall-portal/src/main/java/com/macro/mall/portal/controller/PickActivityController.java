package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.PickActivityService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 仅向商城展示已启用活动及满足原自选区销售规则的商品。 */
@RestController
@RequestMapping("/product/pickActivity")
public class PickActivityController {
    private final PickActivityService service;
    public PickActivityController(PickActivityService service) { this.service = service; }
    @GetMapping
    public CommonResult<List<Map<String,Object>>> list() { return CommonResult.success(service.list(true)); }
    @GetMapping("/{id}")
    public CommonResult<Map<String,Object>> detail(@PathVariable long id) { return CommonResult.success(service.detail(id, true)); }
}
