package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.ConsumeRouteService;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 消费区目的地与旅游线路后台配置入口。 */
@RestController
@RequestMapping("/productCategory/consumeRoute")
public class ConsumeRouteAdminController {
    private final ConsumeRouteService service;
    private final P0AdminAccess access;
    public ConsumeRouteAdminController(ConsumeRouteService service, P0AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/themes")
    public CommonResult<List<Map<String, Object>>> themes(Principal principal) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.listThemes(false));
    }

    @PostMapping("/theme")
    public CommonResult<Long> saveTheme(Principal principal, @RequestBody ConsumeRouteService.Theme theme) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.saveTheme(theme));
    }

    @DeleteMapping("/theme/{id}")
    public CommonResult<?> deleteTheme(Principal principal, @PathVariable long id) {
        access.requireAuthorized(principal);
        service.deleteTheme(id);
        return CommonResult.success(null);
    }

    @GetMapping("/destinations")
    public CommonResult<List<Map<String, Object>>> destinations(Principal principal, @RequestParam(required = false) Long themeId) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.listDestinations(false, themeId));
    }

    @PostMapping("/destination")
    public CommonResult<Long> saveDestination(Principal principal, @RequestBody ConsumeRouteService.Destination destination) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.saveDestination(destination));
    }

    @DeleteMapping("/destination/{id}")
    public CommonResult<?> deleteDestination(Principal principal, @PathVariable long id) {
        access.requireAuthorized(principal);
        service.deleteDestination(id);
        return CommonResult.success(null);
    }

    @GetMapping("/routes")
    public CommonResult<List<Map<String, Object>>> routes(Principal principal, @RequestParam(required = false) Long destinationId) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.listRoutes(false, destinationId));
    }

    @GetMapping("/products")
    public CommonResult<List<Map<String, Object>>> products(Principal principal, @RequestParam(required = false) String keyword) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.candidates(keyword));
    }

    @PostMapping("/route")
    public CommonResult<Long> saveRoute(Principal principal, @RequestBody ConsumeRouteService.Route route) {
        access.requireAuthorized(principal);
        return CommonResult.success(service.saveRoute(route));
    }

    @DeleteMapping("/route/{id}")
    public CommonResult<?> deleteRoute(Principal principal, @PathVariable long id) {
        access.requireAuthorized(principal);
        service.deleteRoute(id);
        return CommonResult.success(null);
    }
}
