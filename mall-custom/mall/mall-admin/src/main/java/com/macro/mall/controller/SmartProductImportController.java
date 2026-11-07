package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.PickProductImportService;
import com.macro.mall.service.SmartImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.Principal;

/** 智能导入：任意表头 Excel/CSV，先预览列识别结果，再按确认后的映射建品。 */
@RestController
@RequestMapping("/p0/zones/{zone}/products/import")
public class SmartProductImportController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SmartProductImportController.class);
    private final P0AdminAccess access;
    private final SmartImportService importer;
    private final PickProductImportService products;

    public SmartProductImportController(P0AdminAccess access, SmartImportService importer, PickProductImportService products) {
        this.access = access;
        this.importer = importer;
        this.products = products;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<SmartImportService.Preview> preview(
            Principal principal, @PathVariable String zone, @RequestPart("file") MultipartFile file) throws IOException {
        access.requireAuthorized(principal);
        return CommonResult.success(importer.preview(zone, file));
    }

    @PostMapping(value = "/smart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<PickProductImportService.Result> smart(
            Principal principal, @PathVariable String zone, @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mapping) throws IOException {
        access.requireAuthorized(principal);
        return CommonResult.success(importer.importFile(zone, file, mapping));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonResult<Void>> partMissing(MissingServletRequestPartException error) {
        LOGGER.warn("智能导入请求里没有文件部件", error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResult.failed("没有收到上传的文件，请重新选择文件后再试"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> rejected(ResponseStatusException error) {
        LOGGER.warn("智能导入被拒绝 status={} reason={}", error.getStatusCode(), error.getReason());
        return ResponseEntity.status(error.getStatusCode()).body(CommonResult.failed(error.getReason()));
    }
}
