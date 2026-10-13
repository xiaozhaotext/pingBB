package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.SystemOpsService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 会员运营后台运维模块：省市区街道地址库、发送记录、访问统计、数据库管理与备份。 */
@RestController
@RequestMapping("/p0/ops")
public class SystemOpsAdminController {
    private final P0AdminAccess access;
    private final SystemOpsService service;
    private final JdbcTemplate jdbc;

    public SystemOpsAdminController(P0AdminAccess access, SystemOpsService service, JdbcTemplate jdbc) {
        this.access = access;
        this.service = service;
        this.jdbc = jdbc;
    }

    // ---------------- 省市区街道地址库 ----------------

    @GetMapping("/areas")
    public CommonResult<List<Map<String, Object>>> areas(Principal p, @RequestParam(defaultValue = "0") long parentId) {
        access.requireAuthorized(p);
        return CommonResult.success(service.areaChildren(parentId));
    }

    @GetMapping("/areas/tree")
    public CommonResult<List<Map<String, Object>>> areaTree(Principal p) {
        access.requireAuthorized(p);
        return CommonResult.success(service.areaTree());
    }

    @PostMapping("/areas")
    public CommonResult<Long> saveArea(Principal p, @RequestBody Map<String, Object> body) {
        long operator = access.requireAuthorized(p);
        long id = service.saveArea(body);
        audit(operator, p.getName(), "area_save", id, "parentId=" + body.get("parentId") + " name=" + body.get("name"));
        return CommonResult.success(id);
    }

    @PostMapping("/areas/delete")
    public CommonResult<Void> deleteArea(Principal p, @RequestBody Map<String, Object> body) {
        long operator = access.requireAuthorized(p);
        long id = body.get("id") == null ? 0L : Long.parseLong(String.valueOf(body.get("id")));
        service.deleteArea(id);
        audit(operator, p.getName(), "area_delete", id, "删除地址 id=" + id);
        return CommonResult.success(null);
    }

    // ---------------- 发送记录 ----------------

    @GetMapping("/messages")
    public CommonResult<Map<String, Object>> messages(Principal p,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String channel,
            @RequestParam(defaultValue = "") String status) {
        access.requireAuthorized(p);
        return CommonResult.success(service.messageRecords(pageNum, pageSize, channel, status));
    }

    @GetMapping("/messages/summary")
    public CommonResult<List<Map<String, Object>>> messageSummary(Principal p, @RequestParam(defaultValue = "30") int days) {
        access.requireAuthorized(p);
        return CommonResult.success(service.messageSummary(days));
    }

    // ---------------- 访问统计 ----------------

    @GetMapping("/visits")
    public CommonResult<Map<String, Object>> visits(Principal p, @RequestParam(defaultValue = "30") int days) {
        access.requireAuthorized(p);
        return CommonResult.success(service.visitSummary(days));
    }

    // ---------------- 数据库管理 ----------------

    @GetMapping("/database/tables")
    public CommonResult<List<Map<String, Object>>> tables(Principal p, @RequestParam(defaultValue = "") String keyword) {
        access.requireAuthorized(p);
        return CommonResult.success(service.tables(keyword));
    }

    @GetMapping("/database/table")
    public CommonResult<Map<String, Object>> table(Principal p, @RequestParam String table) {
        access.requireAuthorized(p);
        return CommonResult.success(service.tableStructure(table));
    }

    @PostMapping("/database/command")
    public CommonResult<List<Map<String, Object>>> command(Principal p, @RequestBody Map<String, Object> body) {
        long operator = access.requireAuthorized(p);
        String action = String.valueOf(body.getOrDefault("action", ""));
        List<String> tables = toStrings(body.get("tables"));
        List<Map<String, Object>> result = service.tableCommand(action, tables);
        audit(operator, p.getName(), "db_" + action.toLowerCase(), 0L, "tables=" + tables);
        return CommonResult.success(result);
    }

    // ---------------- 数据库备份 ----------------

    @GetMapping("/database/backups")
    public CommonResult<List<Map<String, Object>>> backups(Principal p) {
        access.requireAuthorized(p);
        return CommonResult.success(service.listBackups());
    }

    @PostMapping("/database/backup")
    public CommonResult<Map<String, Object>> backup(Principal p) {
        long operator = access.requireAuthorized(p);
        String name = service.createBackup();
        audit(operator, p.getName(), "backup_create", 0L, name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("dir", service.backupDir().toString());
        return CommonResult.success(result);
    }

    @GetMapping("/database/backup/download")
    public ResponseEntity<Resource> download(Principal p, @RequestParam String name) {
        long operator = access.requireAuthorized(p);
        Path path = service.resolveBackup(name);
        audit(operator, p.getName(), "backup_download", 0L, name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    @PostMapping("/database/backup/delete")
    public CommonResult<Void> deleteBackup(Principal p, @RequestBody Map<String, Object> body) {
        long operator = access.requireAuthorized(p);
        String name = String.valueOf(body.getOrDefault("name", ""));
        service.deleteBackup(name);
        audit(operator, p.getName(), "backup_delete", 0L, name);
        return CommonResult.success(null);
    }

    @PostMapping("/database/backup/restore")
    public CommonResult<Map<String, Object>> restore(Principal p, @RequestBody Map<String, Object> body) {
        long operator = access.requireAuthorized(p);
        String name = String.valueOf(body.getOrDefault("name", ""));
        Map<String, Object> result = service.restoreBackup(name);
        audit(operator, p.getName(), "backup_restore", 0L, "from=" + name + " safety=" + result.get("safetyBackup"));
        return CommonResult.success(result);
    }

    private static List<String> toStrings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private void audit(long id, String name, String action, long targetId, String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,?,?,?,?)",
                id, name, "system_ops", action, targetId, detail);
    }
}
