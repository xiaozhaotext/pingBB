package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.service.MallCategoryCatalog;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/p0/mall-category-display")
public class MallCategoryDisplayController {
    private final MallCategoryCatalog catalog;
    private final P0AdminAccess access;
    public MallCategoryDisplayController(MallCategoryCatalog catalog,P0AdminAccess access) {this.catalog=catalog;this.access=access;}
    @GetMapping
    public CommonResult<?> list(Principal principal) {access.requireAuthorized(principal);return CommonResult.success(catalog.snapshot());}
    public record Request(long revision,Long id,String name,Long parentId,Integer sort,Boolean visible,boolean reset) {}
    @PostMapping
    public CommonResult<?> update(Principal principal,@RequestBody Request request) {
        access.requireAuthorized(principal);
        try {
            if(request==null || request.revision()<0)throw new IllegalArgumentException("请刷新分类列表后重试");
            return CommonResult.success(catalog.update(request.revision(),new MallCategoryCatalog.Change(request.id(),request.name(),request.parentId(),request.sort(),request.visible()),request.reset()));
        } catch(IllegalArgumentException ex) {return CommonResult.validateFailed(ex.getMessage());}
    }
}
