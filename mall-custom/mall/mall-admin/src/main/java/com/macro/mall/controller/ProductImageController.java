package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/product/image")
public class ProductImageController {
    @Value("${mall.product.image-dir:/opt/mall-demo/product-images}")
    private String imageDir;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<Map<String, String>> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能为空且不能超过10MB");
        }
        String type = file.getContentType();
        String extension;
        if ("image/jpeg".equals(type)) extension = ".jpg";
        else if ("image/png".equals(type)) extension = ".png";
        else if ("image/webp".equals(type)) extension = ".webp";
        else if ("image/gif".equals(type)) extension = ".gif";
        else throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、GIF 图片");
        Path directory = Path.of(imageDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String filename = UUID.randomUUID() + extension;
        Path target = directory.resolve(filename);
        try (var input = file.getInputStream()) {
            Files.copy(input, target);
        }
        return CommonResult.success(Map.of("url", "/product-images/" + filename));
    }
}
