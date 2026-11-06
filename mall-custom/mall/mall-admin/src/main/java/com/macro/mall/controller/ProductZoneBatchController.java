package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.ProductZoneBatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/p0/zones/{zone}/products")
public class ProductZoneBatchController {
    private final ProductZoneBatchService service;
    private final P0AdminAccess access;

    public ProductZoneBatchController(ProductZoneBatchService service, P0AdminAccess access) {
        this.service = service;
        this.access = access;
    }

    public record Selection(List<Long> ids) {}

    @PostMapping("/batch-trash")
    public CommonResult<Integer> trash(Principal principal, @PathVariable String zone, @RequestBody Selection request) {
        long operator = access.requireAuthorized(principal);
        return CommonResult.success(service.trash(zone, request.ids(), operator, principal.getName()));
    }

    @PostMapping("/batch-publish")
    public CommonResult<Integer> publish(Principal principal, @PathVariable String zone, @RequestBody Selection request) {
        long operator = access.requireAuthorized(principal);
        return CommonResult.success(service.publish(zone, request.ids(), operator, principal.getName()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(CommonResult.failed(exception.getReason()));
    }
}
