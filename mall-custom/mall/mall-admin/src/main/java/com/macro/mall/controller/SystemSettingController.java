package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.config.AdminIpAccessFilter;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.SystemSettingService;
import com.macro.mall.service.SystemSettingService.IpAccessRule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Platform settings centre: company profile, watermark, mini program, IP access, cache maintenance
 * and the image asset browser. Every write is audited; reads mask stored secrets.
 */
@RestController
@RequestMapping("/p0/system-settings")
public class SystemSettingController {
    private static final int MAX_ASSETS = 2000;
    private static final int MAX_PAGE_SIZE = 200;

    /** Image URLs referenced by the platform's own tables, with the owning module. */
    private static final String ASSET_SQL =
            "SELECT 'product' AS source, p.pic AS url FROM pms_product p WHERE p.pic IS NOT NULL AND p.pic <> '' "
            + "UNION ALL SELECT 'product_album', TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p.album_pics, ',', n.n), ',', -1)) "
            + "FROM pms_product p JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 "
            + "UNION ALL SELECT 4 UNION ALL SELECT 5) n "
            + "ON n.n <= 1 + LENGTH(p.album_pics) - LENGTH(REPLACE(p.album_pics, ',', '')) "
            + "WHERE p.album_pics IS NOT NULL AND p.album_pics <> '' "
            + "UNION ALL SELECT 'sku', s.pic FROM pms_sku_stock s WHERE s.pic IS NOT NULL AND s.pic <> '' "
            + "UNION ALL SELECT 'brand', b.logo FROM pms_brand b WHERE b.logo IS NOT NULL AND b.logo <> '' "
            + "UNION ALL SELECT 'advertise', a.pic FROM sms_home_advertise a WHERE a.pic IS NOT NULL AND a.pic <> '' "
            + "UNION ALL SELECT 'category', c.icon FROM pms_product_category c WHERE c.icon IS NOT NULL AND c.icon <> '' "
            + "UNION ALL SELECT 'member_avatar', m.icon FROM ums_member m "
            + "WHERE m.icon IS NOT NULL AND m.icon <> '' AND m.icon NOT LIKE '/static/%'";

    private static final Map<String, String> SOURCE_LABELS = Map.of(
            "product", "商品主图",
            "product_album", "商品相册",
            "sku", "规格图片",
            "brand", "品牌 Logo",
            "advertise", "首页广告",
            "category", "分类图标",
            "member_avatar", "会员头像");

    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;
    private final SystemSettingService settings;

    public SystemSettingController(JdbcTemplate jdbc, P0AdminAccess access, SystemSettingService settings) {
        this.jdbc = jdbc;
        this.access = access;
        this.settings = settings;
    }

    @GetMapping
    public CommonResult<Map<String, Object>> read(Principal principal, HttpServletRequest request) {
        access.requireAuthorized(principal);
        Map<String, Object> data = settings.grouped();
        IpAccessRule rule = settings.ipAccessRule();
        data.put("currentIp", AdminIpAccessFilter.resolveClientIp(request));
        data.put("ipAccess", Map.of(
                "enabled", rule.enabled(),
                "mode", rule.mode(),
                "entries", rule.tokens().size()));
        data.put("contentKeys", List.of(SystemSettingService.CONTENT_PRIVACY_POLICY,
                SystemSettingService.CONTENT_USER_AGREEMENT));
        return CommonResult.success(data);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> update(Principal principal, HttpServletRequest request,
                                     @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String key = text(body, "key", 3, 64);
        String value = body.get("value") == null ? "" : String.valueOf(body.get("value"));
        settings.update(key, value, operatorId, principal.getName(), AdminIpAccessFilter.resolveClientIp(request));
        return CommonResult.success(null);
    }

    @PostMapping("/cache/clear")
    public CommonResult<Map<String, Object>> clearCache(Principal principal, HttpServletRequest request) {
        long operatorId = access.requireAuthorized(principal);
        return CommonResult.success(settings.clearAdminCache(operatorId, principal.getName(),
                AdminIpAccessFilter.resolveClientIp(request)));
    }

    @GetMapping("/content/{key}")
    public CommonResult<Map<String, Object>> content(Principal principal, @PathVariable String key) {
        access.requireAuthorized(principal);
        return CommonResult.success(settings.content(key));
    }

    @PostMapping("/content")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> updateContent(Principal principal, HttpServletRequest request,
                                            @RequestBody Map<String, Object> body) {
        long operatorId = access.requireAuthorized(principal);
        String key = text(body, "key", 3, 64);
        String value = body.get("value") == null ? "" : String.valueOf(body.get("value"));
        settings.updateContent(key, value, operatorId, principal.getName(),
                AdminIpAccessFilter.resolveClientIp(request));
        return CommonResult.success(null);
    }

    /**
     * Image asset browser over the images the platform itself references. mode=list|grid|folder|stats.
     * The same payload feeds all three views, so switching modes never triggers a second request shape.
     */
    @GetMapping("/image-assets")
    public CommonResult<Map<String, Object>> imageAssets(Principal principal,
                                                        @RequestParam(defaultValue = "list") String mode,
                                                        @RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "60") int size) {
        access.requireAuthorized(principal);
        String normalizedMode = List.of("list", "grid", "folder", "stats").contains(mode) ? mode : "list";
        String like = "%" + keyword.trim() + "%";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT source, url FROM (" + ASSET_SQL + ") AS assets WHERE url LIKE ? AND url <> '' LIMIT "
                        + MAX_ASSETS, like);

        List<Map<String, Object>> assets = new ArrayList<>(rows.size());
        Map<String, Integer> bySource = new TreeMap<>();
        Map<String, Integer> byScheme = new TreeMap<>();
        Map<String, Integer> byExtension = new TreeMap<>();
        Map<String, Map<String, Object>> folders = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String source = String.valueOf(row.get("source"));
            String url = String.valueOf(row.get("url"));
            String scheme = url.startsWith("https://") ? "https" : url.startsWith("http://") ? "http" : "relative";
            String directory = directory(url);
            String extension = extension(url);
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("url", url);
            asset.put("source", source);
            asset.put("sourceLabel", SOURCE_LABELS.getOrDefault(source, source));
            asset.put("directory", directory);
            asset.put("fileName", fileName(url));
            asset.put("extension", extension);
            asset.put("scheme", scheme);
            assets.add(asset);
            bySource.merge(SOURCE_LABELS.getOrDefault(source, source), 1, Integer::sum);
            byScheme.merge(scheme, 1, Integer::sum);
            byExtension.merge(extension, 1, Integer::sum);
            Map<String, Object> folder = folders.computeIfAbsent(directory, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("directory", key);
                created.put("count", 0);
                created.put("sources", new TreeMap<String, Integer>());
                return created;
            });
            folder.put("count", Integer.parseInt(String.valueOf(folder.get("count"))) + 1);
            @SuppressWarnings("unchecked")
            Map<String, Integer> folderSources = (Map<String, Integer>) folder.get("sources");
            folderSources.merge(SOURCE_LABELS.getOrDefault(source, source), 1, Integer::sum);
        }

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 1);
        int from = Math.min((pageNo - 1) * pageSize, assets.size());
        int to = Math.min(from + pageSize, assets.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", normalizedMode);
        data.put("keyword", keyword.trim());
        data.put("total", assets.size());
        data.put("page", pageNo);
        data.put("size", pageSize);
        data.put("assets", assets.subList(from, to));
        data.put("folders", new ArrayList<>(folders.values()));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", assets.size());
        stats.put("bySource", bySource);
        stats.put("byScheme", byScheme);
        stats.put("byExtension", byExtension);
        stats.put("directoryCount", folders.size());
        stats.put("nonHttps", byScheme.getOrDefault("http", 0));
        data.put("stats", stats);
        data.put("note", "统计范围为数据库中引用的图片；未扫描服务器静态目录");
        return CommonResult.success(data);
    }

    private static String directory(String url) {
        String withoutQuery = url.split("[?#]")[0];
        int slash = withoutQuery.lastIndexOf('/');
        if (slash <= 0) {
            return "/";
        }
        String path = withoutQuery.substring(0, slash + 1);
        int schemeEnd = path.indexOf("//");
        if (schemeEnd >= 0) {
            int hostEnd = path.indexOf('/', schemeEnd + 2);
            return hostEnd < 0 ? path : path.substring(hostEnd);
        }
        return path;
    }

    private static String fileName(String url) {
        String withoutQuery = url.split("[?#]")[0];
        int slash = withoutQuery.lastIndexOf('/');
        return slash < 0 ? withoutQuery : withoutQuery.substring(slash + 1);
    }

    private static String extension(String url) {
        String name = fileName(url);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "(无扩展名)";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.length() > 8 ? "(未知)" : extension;
    }

    private static String text(Map<String, Object> body, String key, int min, int max) {
        String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
        if (value.length() < min || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " 长度无效");
        }
        return value;
    }
}
