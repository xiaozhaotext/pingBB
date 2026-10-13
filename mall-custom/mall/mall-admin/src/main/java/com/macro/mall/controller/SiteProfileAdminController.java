package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.SiteProfileService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/p0/site-profile")
public class SiteProfileAdminController {
    private final P0AdminAccess access;
    private final SiteProfileService service;
    public SiteProfileAdminController(P0AdminAccess access,SiteProfileService service) { this.access=access;this.service=service; }
    @GetMapping
    public CommonResult<Map<String,Object>> get(Principal p) { access.requireAuthorized(p);return CommonResult.success(service.read()); }
    @PostMapping("/company")
    public CommonResult<Void> company(Principal p,@RequestBody Map<String,Object> body) {long id=access.requireAuthorized(p);service.company(body,id,p.getName());return CommonResult.success(null);}
    @PostMapping("/privacy/draft")
    public CommonResult<Void> draft(Principal p,@RequestBody Map<String,Object> body) {long id=access.requireAuthorized(p);service.draft(body,id,p.getName());return CommonResult.success(null);}
    @PostMapping("/privacy/publish")
    public CommonResult<Void> publish(Principal p,@RequestBody Map<String,Object> body) {long id=access.requireAuthorized(p);service.publish(body,id,p.getName());return CommonResult.success(null);}
}
