package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.ConsumeRouteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 消费区目的地与旅游线路：只展示启用且可售的内容，结算仍走原商品系统。 */
@RestController
@RequestMapping("/product/consumeRoute")
public class ConsumeRouteController {
    private final ConsumeRouteService service;
    public ConsumeRouteController(ConsumeRouteService service) { this.service = service; }

    @GetMapping("/themes")
    public CommonResult<List<Map<String, Object>>> themes() {
        return CommonResult.success(service.listThemes(true));
    }

    @GetMapping("/destinations")
    public CommonResult<List<Map<String, Object>>> destinations(@RequestParam(required = false) Long themeId) {
        return CommonResult.success(service.listDestinations(true, themeId));
    }

    @GetMapping("/routes")
    public CommonResult<List<Map<String, Object>>> routes(@RequestParam(required = false) Long destinationId) {
        return CommonResult.success(service.listRoutes(true, destinationId));
    }
}
