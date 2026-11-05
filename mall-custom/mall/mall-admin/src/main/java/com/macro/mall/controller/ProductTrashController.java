package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.ProductTrashService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/p0/zones/{zone}")
public class ProductTrashController {
    private final ProductTrashService service;
    private final P0AdminAccess access;
    public ProductTrashController(ProductTrashService service,P0AdminAccess access) { this.service=service;this.access=access; }
    public record PurgeRequest(List<Long> ids,boolean all) {}
    public record Policy(int days) {}
    @GetMapping("/trash")
    public CommonResult<?> list(Principal p,@PathVariable String zone,@RequestParam(defaultValue="") String keyword) {
        access.requireAuthorized(p);return CommonResult.success(service.list(zone,keyword));
    }
    @PostMapping("/products/{id}/trash")
    public CommonResult<?> trash(Principal p,@PathVariable String zone,@PathVariable long id) {
        service.trash(zone,id,access.requireAuthorized(p),p.getName());return CommonResult.success(null);
    }
    @PostMapping("/trash/{id}/restore")
    public CommonResult<?> restore(Principal p,@PathVariable String zone,@PathVariable long id) {
        service.restore(zone,id,access.requireAuthorized(p),p.getName());return CommonResult.success(null);
    }
    @PostMapping("/trash/purge")
    public CommonResult<?> purge(Principal p,@PathVariable String zone,@RequestBody PurgeRequest request) {
        return CommonResult.success(service.purge(zone,request.ids(),request.all(),access.requireAuthorized(p),p.getName()));
    }
    @GetMapping("/trash/policy")
    public CommonResult<?> policy(Principal p,@PathVariable String zone) {
        access.requireAuthorized(p);return CommonResult.success(Map.of("days",service.days(zone)));
    }
    @PostMapping("/trash/policy")
    public CommonResult<?> policy(Principal p,@PathVariable String zone,@RequestBody Policy policy) {
        service.setDays(zone,policy.days(),access.requirePlatformAdmin(p),p.getName());return CommonResult.success(null);
    }
}
