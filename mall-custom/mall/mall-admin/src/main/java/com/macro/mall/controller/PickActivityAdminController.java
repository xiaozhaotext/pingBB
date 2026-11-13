package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.PickActivityService;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 分类管理中的自选区旅游活动配置入口。 */
@RestController
@RequestMapping("/productCategory/pickActivity")
public class PickActivityAdminController {
    private final PickActivityService service;
    private final P0AdminAccess access;
    public PickActivityAdminController(PickActivityService service, P0AdminAccess access) {
        this.service = service;
        this.access = access;
    }
    @GetMapping
    public CommonResult<List<Map<String,Object>>> list(Principal principal) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.list(false));
    }
    @GetMapping("/products")
    public CommonResult<List<Map<String,Object>>> products(Principal principal, @RequestParam(required=false) String keyword) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.candidates(keyword));
    }
    @GetMapping("/{id}")
    public CommonResult<Map<String,Object>> detail(Principal principal, @PathVariable long id) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.detail(id, false));
    }
    @PostMapping
    public CommonResult<Long> save(Principal principal, @RequestBody PickActivityService.Activity activity) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.save(activity));
    }
}
