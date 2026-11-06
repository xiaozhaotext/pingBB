package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.CkirProductImportService;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;

@RestController
@RequestMapping("/p0/ckir/products")
public class CkirProductImportController {
    private final P0AdminAccess access;
    private final CkirProductImportService service;

    public CkirProductImportController(P0AdminAccess access, CkirProductImportService service) {
        this.access = access;
        this.service = service;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<CkirProductImportService.Preview> preview(
            Principal principal, @RequestPart("file") MultipartFile file) throws IOException {
        access.requireAuthorized(principal);
        return CommonResult.success(service.preview(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<CkirProductImportService.Result> importFile(
            Principal principal, @RequestPart("file") MultipartFile file) throws IOException {
        access.requireAuthorized(principal);
        return CommonResult.success(service.importFile(file));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> importError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(CommonResult.failed(error.getReason()));
    }
}
