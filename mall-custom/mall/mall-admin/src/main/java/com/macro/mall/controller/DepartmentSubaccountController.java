package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.UmsAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Department-bound administrator accounts. A department role controls menus and API resources. */
@RestController
@RequestMapping("/p0/subaccounts")
public class DepartmentSubaccountController {
    private final JdbcTemplate jdbc;
    private final P0AdminAccess access;
    private final UmsAdminService admins;
    @Value("${mall.p0.admin-role-id:5}")
    private long platformRoleId;

    public DepartmentSubaccountController(JdbcTemplate jdbc, P0AdminAccess access, UmsAdminService admins) {
        this.jdbc = jdbc;
        this.access = access;
        this.admins = admins;
    }

    @GetMapping
    public CommonResult<List<Map<String, Object>>> list(Principal principal) {
        access.requirePlatformAdmin(principal);
        return CommonResult.success(jdbc.queryForList(
                "SELECT a.id,a.username,a.nick_name,a.phone,a.status,a.dept_id,d.name AS dept_name "
                        + "FROM ums_admin a LEFT JOIN ums_department d ON d.id=a.dept_id "
                        + "WHERE a.dept_id IS NOT NULL ORDER BY a.id DESC"));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Map<String, Long>> create(Principal principal, @RequestBody Map<String, Object> body) {
        long operator = access.requirePlatformAdmin(principal);
        String username = text(body, "username", 40);
        if (!username.matches("[A-Za-z0-9._-]{3,40}")) bad("账号格式无效");
        String password = password(body);
        long department = department(body);
        UmsAdminParam param = new UmsAdminParam();
        param.setUsername(username);
        param.setPassword(password);
        param.setNickName(text(body, "nickName", 80));
        String phone = phone(body);
        UmsAdmin created = admins.register(param);
        if (created == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        jdbc.update("UPDATE ums_admin SET dept_id=?,phone=? WHERE id=?", department, phone, created.getId());
        audit(operator, principal.getName(), "create", created.getId(), department);
        return CommonResult.success(Map.of("id", created.getId()));
    }

    @PostMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Void> update(Principal principal, @PathVariable long id,
                                     @RequestBody Map<String, Object> body) {
        long operator = access.requirePlatformAdmin(principal);
        Long existing = jdbc.queryForObject("SELECT dept_id FROM ums_admin WHERE id=?", Long.class, id);
        if (existing == null) bad("只能编辑部门子账号");
        long department = department(body);
        UmsAdmin change = new UmsAdmin();
        change.setNickName(text(body, "nickName", 80));
        String phone = phone(body);
        int status = number(body, "status").intValue();
        if (status != 0 && status != 1) bad("状态无效");
        change.setStatus(status);
        if (body.get("password") != null && !String.valueOf(body.get("password")).isBlank()) {
            change.setPassword(password(body));
        }
        admins.update(id, change);
        jdbc.update("UPDATE ums_admin SET dept_id=?,phone=? WHERE id=? AND dept_id IS NOT NULL", department, phone, id);
        admins.getCacheService().delAdmin(id);
        admins.getCacheService().delResourceList(id);
        audit(operator, principal.getName(), "update", id, department);
        return CommonResult.success(null);
    }

    private long department(Map<String, Object> body) {
        long id = number(body, "departmentId").longValue();
        Long valid = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ums_department d JOIN ums_role r ON r.id=d.role_id "
                        + "WHERE d.id=? AND d.status=1 AND r.status=1 AND r.id<>?", Long.class, id, platformRoleId);
        if (valid == null || valid != 1) bad("请先为启用的部门配置有效角色");
        return id;
    }

    private Number number(Map<String, Object> body, String key) {
        try { return Long.parseLong(String.valueOf(body.get(key))); }
        catch (RuntimeException ex) { bad(key + " 无效"); return 0; }
    }

    private String text(Map<String, Object> body, String key, int max) {
        String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
        if (value.length() > max) bad(key + " 过长");
        return value;
    }

    private String password(Map<String, Object> body) {
        String value = body.get("password") == null ? "" : String.valueOf(body.get("password"));
        if (value.length() < 8 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            bad("密码须为 8 至 72 字节");
        return value;
    }

    private String phone(Map<String, Object> body) {
        String value = text(body, "phone", 11);
        if (!value.matches("1[3-9][0-9]{9}")) bad("请输入 11 位手机号码");
        return value;
    }

    private void audit(long operator, String name, String action, long account, long department) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) "
                        + "VALUES(?,?,'subaccount',?,?,?)", operator, name, action, account,
                "department=" + department);
    }

    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
