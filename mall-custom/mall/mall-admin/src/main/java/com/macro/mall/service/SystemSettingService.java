package com.macro.mall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Admin-editable platform settings: company profile, image watermark, mini program credentials
 * and IP access control. Values live in sys_config; long compliance texts live in sys_content.
 *
 * Only the keys registered here can be written through the settings API, so a compromised admin
 * session cannot rewrite unrelated configuration (payment keys stay on their own P0 endpoints).
 */
@Component
public class SystemSettingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemSettingService.class);

    public static final String GROUP_COMPANY = "company";
    public static final String GROUP_WATERMARK = "watermark";
    public static final String GROUP_MINIAPP = "miniapp";
    public static final String GROUP_IP_ACCESS = "ip_access";
    public static final String GROUP_UPLOAD = "upload";
    public static final String GROUP_DISTRIBUTION = "distribution";
    public static final String GROUP_SMS = "sms";

    public static final String KEY_IP_ENABLED = "ip.access.enabled";
    public static final String KEY_IP_MODE = "ip.access.mode";
    public static final String KEY_IP_LIST = "ip.access.list";

    public static final String CONTENT_PRIVACY_POLICY = "privacy_policy";
    public static final String CONTENT_USER_AGREEMENT = "user_agreement";
    public static final String CONTENT_REGISTER_NOTICE = "register_notice";
    private static final int CONTENT_MAX_LENGTH = 60000;

    private static final int MAX_VALUE_LENGTH = 512;
    private static final int MAX_IP_ENTRIES = 32;
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV4_CIDR = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}/(\\d{1,2})$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\- ]{3,32}$");
    private static final Pattern QQ = Pattern.compile("^[0-9]{5,20}$");

    /** Ordered registry of writable keys. Order drives the admin page layout. */
    private static final Map<String, KeySpec> KEYS;

    static {
        Map<String, KeySpec> keys = new LinkedHashMap<>();
        keys.put("company.name", new KeySpec(GROUP_COMPANY, "公司名称", false, SystemSettingService::validateCompanyName));
        keys.put("company.wechat_qrcode", new KeySpec(GROUP_COMPANY, "微信二维码", false, SystemSettingService::validateQrUrl));
        keys.put("company.address", new KeySpec(GROUP_COMPANY, "公司地址", false, SystemSettingService::validatePlain));
        keys.put("company.phone", new KeySpec(GROUP_COMPANY, "公司电话", false, SystemSettingService::validatePhone));
        keys.put("company.mobile", new KeySpec(GROUP_COMPANY, "手机号码", false, SystemSettingService::validatePhone));
        keys.put("company.email", new KeySpec(GROUP_COMPANY, "公司邮箱", false, SystemSettingService::validateEmail));
        keys.put("company.qq", new KeySpec(GROUP_COMPANY, "客服 QQ", false, SystemSettingService::validateQq));
        keys.put("watermark.enabled", new KeySpec(GROUP_WATERMARK, "启用水印", false, SystemSettingService::validateSwitch));
        keys.put("watermark.text", new KeySpec(GROUP_WATERMARK, "水印文字", false, SystemSettingService::validatePlain));
        keys.put("watermark.position", new KeySpec(GROUP_WATERMARK, "水印位置", false, SystemSettingService::validateWatermarkPosition));
        keys.put("watermark.opacity", new KeySpec(GROUP_WATERMARK, "不透明度", false, SystemSettingService::validateOpacity));
        keys.put("watermark.size", new KeySpec(GROUP_WATERMARK, "字号(px)", false, SystemSettingService::validateWatermarkSize));
        keys.put("miniapp.name", new KeySpec(GROUP_MINIAPP, "小程序名称", false, SystemSettingService::validatePlain));
        keys.put("miniapp.appid", new KeySpec(GROUP_MINIAPP, "小程序 AppID", false, SystemSettingService::validatePlain));
        keys.put("miniapp.secret", new KeySpec(GROUP_MINIAPP, "小程序 AppSecret", true, SystemSettingService::validatePlain));
        keys.put(KEY_IP_ENABLED, new KeySpec(GROUP_IP_ACCESS, "启用 IP 限制", false, SystemSettingService::validateSwitch));
        keys.put(KEY_IP_MODE, new KeySpec(GROUP_IP_ACCESS, "限制模式", false, SystemSettingService::validateIpMode));
        keys.put(KEY_IP_LIST, new KeySpec(GROUP_IP_ACCESS, "IP 列表", false, SystemSettingService::validateIpList));
        keys.put("upload.image_max_size", new KeySpec(GROUP_UPLOAD, "图片大小上限(MB)", false, SystemSettingService::validatePositiveInt));
        keys.put("upload.image_extensions", new KeySpec(GROUP_UPLOAD, "允许的图片格式", false, SystemSettingService::validateExtensionList));
        keys.put("upload.image_max_width", new KeySpec(GROUP_UPLOAD, "图片最大宽度(px)", false, SystemSettingService::validatePositiveInt));
        keys.put("upload.image_optimize", new KeySpec(GROUP_UPLOAD, "启用图片压缩优化", false, SystemSettingService::validateSwitch));
        keys.put("upload.attachment_max_size", new KeySpec(GROUP_UPLOAD, "附件大小上限(MB)", false, SystemSettingService::validatePositiveInt));
        keys.put("distribution.enabled", new KeySpec(GROUP_DISTRIBUTION, "分销体系总开关", false, SystemSettingService::validateSwitch));
        keys.put("dividend.distribution.enabled", new KeySpec(GROUP_DISTRIBUTION, "每日分红发放开关", false, SystemSettingService::validateSwitch));
        keys.put("dividend.pool.ratio", new KeySpec(GROUP_DISTRIBUTION, "购买金额进分红池比例(0-1)", false, SystemSettingService::validateRatio));
        keys.put("dividend.daily.base", new KeySpec(GROUP_DISTRIBUTION, "每日每份分红基数(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("dividend.cap.base", new KeySpec(GROUP_DISTRIBUTION, "无分享时分红封顶(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("dividend.cap.per_referral", new KeySpec(GROUP_DISTRIBUTION, "每推荐1人封顶增加(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("dividend.cap.max", new KeySpec(GROUP_DISTRIBUTION, "分红封顶上限(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("membership.referral.cash_rate", new KeySpec(GROUP_DISTRIBUTION, "直推现金奖励比例(0-1)", false, SystemSettingService::validateRatio));
        keys.put("membership.referral.partner.cash_cap", new KeySpec(GROUP_DISTRIBUTION, "合伙人直推现金封顶(元,空=不限)", false, SystemSettingService::validateDecimalOrBlank));
        keys.put("membership.referral.cofounder.cash_cap", new KeySpec(GROUP_DISTRIBUTION, "联创直推现金封顶(元,空=不限)", false, SystemSettingService::validateDecimalOrBlank));
        keys.put("membership.referral.partner.repurchase_points", new KeySpec(GROUP_DISTRIBUTION, "合伙人直推赠送复购积分", false, SystemSettingService::validateNonNegativeLong));
        keys.put("membership.referral.cofounder.repurchase_points", new KeySpec(GROUP_DISTRIBUTION, "联创直推赠送复购积分", false, SystemSettingService::validateNonNegativeLong));
        keys.put("share.order.bonus.enabled", new KeySpec(GROUP_DISTRIBUTION, "分享下单奖励开关", false, SystemSettingService::validateSwitch));
        keys.put("share.order.bonus", new KeySpec(GROUP_DISTRIBUTION, "分享每单奖励(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("rebuy.points.ratio", new KeySpec(GROUP_DISTRIBUTION, "复购区积分抵扣比例(0-1)", false, SystemSettingService::validateRatio));
        keys.put("product.points.yuan_per_point", new KeySpec(GROUP_DISTRIBUTION, "每产品积分抵扣金额(元)", false, SystemSettingService::validatePositiveDecimal));
        keys.put("product.points.max_ratio", new KeySpec(GROUP_DISTRIBUTION, "产品积分单笔最高抵扣比例(0-1)", false, SystemSettingService::validateRatio));
        keys.put("withdraw.fee_rate", new KeySpec(GROUP_DISTRIBUTION, "提现手续费率(0-1)", false, SystemSettingService::validateRatio));
        keys.put("withdraw.min_amount", new KeySpec(GROUP_DISTRIBUTION, "单笔提现最低金额(元)", false, SystemSettingService::validateNonNegativeDecimal));
        keys.put("sms.provider", new KeySpec(GROUP_SMS, "短信服务商（aliyun/tencent，空=未配置）", false, SystemSettingService::validateSmsProvider));
        keys.put("sms.access_key", new KeySpec(GROUP_SMS, "短信服务商 AccessKey", false, SystemSettingService::validatePlain));
        keys.put("sms.access_secret", new KeySpec(GROUP_SMS, "短信服务商 AccessSecret", true, SystemSettingService::validatePlain));
        keys.put("sms.sign_name", new KeySpec(GROUP_SMS, "短信签名", false, SystemSettingService::validatePlain));
        keys.put("sms.template_code", new KeySpec(GROUP_SMS, "短信模板 Code", false, SystemSettingService::validatePlain));
        KEYS = Collections.unmodifiableMap(keys);
    }

    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redisConnectionFactory;

    @Value("${redis.database:mall}")
    private String redisDatabase;
    @Value("${redis.key.admin:ums:admin}")
    private String redisKeyAdmin;
    @Value("${redis.key.resourceList:ums:resourceList}")
    private String redisKeyResourceList;

    public SystemSettingService(JdbcTemplate jdbc, RedisConnectionFactory redisConnectionFactory) {
        this.jdbc = jdbc;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /** Every editable key with its current value, grouped for the admin page. Secrets come back masked. */
    public Map<String, Object> grouped() {
        Map<String, String> stored = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT config_key,config_value FROM sys_config WHERE config_key IN (" + placeholders(KEYS.size()) + ")",
                KEYS.keySet().toArray())) {
            stored.put(String.valueOf(row.get("config_key")),
                    row.get("config_value") == null ? null : String.valueOf(row.get("config_value")));
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        groups.put(GROUP_COMPANY, new ArrayList<>());
        groups.put(GROUP_WATERMARK, new ArrayList<>());
        groups.put(GROUP_MINIAPP, new ArrayList<>());
        groups.put(GROUP_IP_ACCESS, new ArrayList<>());
        groups.put(GROUP_UPLOAD, new ArrayList<>());
        groups.put(GROUP_DISTRIBUTION, new ArrayList<>());
        groups.put(GROUP_SMS, new ArrayList<>());
        for (Map.Entry<String, KeySpec> entry : KEYS.entrySet()) {
            KeySpec spec = entry.getValue();
            String raw = stored.get(entry.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("config_key", entry.getKey());
            item.put("label", spec.label());
            item.put("secret", spec.secret());
            item.put("config_value", spec.secret() ? maskSecret(raw) : raw);
            item.put("configured", raw != null && !raw.isEmpty());
            groups.get(spec.group()).add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", groups);
        return data;
    }

    public void update(String key, String rawValue, long operatorId, String operatorName, String clientIp) {
        KeySpec spec = KEYS.get(key);
        if (spec == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "此配置项不可修改");
        }
        String value = rawValue == null ? "" : rawValue.trim();
        // Resubmitting the masked display value means "keep the stored secret".
        if (spec.secret() && value.contains("****")) {
            return;
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置内容不能超过 " + MAX_VALUE_LENGTH + " 个字符");
        }
        spec.validator().validate(value);
        if (KEY_IP_ENABLED.equals(key) && "1".equals(value) && loadRaw(KEY_IP_LIST).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "启用 IP 限制前必须先配置 IP 列表");
        }
        if (GROUP_COMPANY.equals(spec.group())) jdbc.update("UPDATE sys_site_profile SET company_revision=company_revision+1 WHERE id=1");
        List<String> old = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=? FOR UPDATE",
                (rs, n) -> rs.getString(1), key);
        jdbc.update("INSERT INTO sys_config(config_key,config_value,config_group,remark) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), config_group=VALUES(config_group)",
                key, value.isEmpty() ? null : value, spec.group(), spec.label());
        audit(operatorId, operatorName, "settings", "update", key,
                maskIfSecret(spec, old.isEmpty() ? null : old.get(0)) + " -> " + maskIfSecret(spec, value), clientIp);
    }

    /** Long compliance texts (privacy policy, user agreement) kept in sys_content. */
    public Map<String, Object> content(String contentKey) {
        requireContentKey(contentKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content_key", contentKey);
        data.put("content_value", null);
        data.put("update_time", null);
        data.put("migration_required", false);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT content_value,update_time FROM sys_content WHERE content_key=?", contentKey);
            if (!rows.isEmpty()) {
                data.put("content_value", rows.get(0).get("content_value"));
                data.put("update_time", rows.get(0).get("update_time"));
            }
        } catch (org.springframework.dao.DataAccessException e) {
            LOGGER.warn("sys_content unavailable; apply 22_admin_settings_center.sql: {}", e.getMessage());
            data.put("migration_required", true);
        }
        return data;
    }

    public void updateContent(String contentKey, String value, long operatorId, String operatorName, String clientIp) {
        if(CONTENT_PRIVACY_POLICY.equals(contentKey)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请使用公司信息与隐私政策页面保存草稿并审核发布");
        requireContentKey(contentKey);
        String text = value == null ? "" : value.trim();
        if (text.length() > CONTENT_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不能超过 " + CONTENT_MAX_LENGTH + " 个字符");
        }
        jdbc.update("INSERT INTO sys_content(content_key,content_value,operator) VALUES(?,?,?) "
                        + "ON DUPLICATE KEY UPDATE content_value=VALUES(content_value), operator=VALUES(operator)",
                contentKey, text, operatorName);
        audit(operatorId, operatorName, "settings", "update_content", contentKey,
                "length=" + text.length(), clientIp);
    }

    /**
     * Clears the admin permission caches only. Session tokens are stateless JWTs, and portal
     * idempotency keys are deliberately left alone, so no payment or session state is lost.
     */
    public Map<String, Object> clearAdminCache(long operatorId, String operatorName, String clientIp) {
        List<String> prefixes = List.of(redisDatabase + ":" + redisKeyAdmin, redisDatabase + ":" + redisKeyResourceList);
        long deleted = 0;
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            for (String prefix : prefixes) {
                deleted += deleteByPrefix(connection, prefix);
            }
        }
        audit(operatorId, operatorName, "settings", "clear_cache", null, String.join(",", prefixes), clientIp);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        data.put("prefixes", prefixes);
        data.put("note", "仅清理后台权限缓存；登录令牌为无状态 JWT，不会被强制下线");
        return data;
    }

    public IpAccessRule ipAccessRule() {
        String enabled = loadRaw(KEY_IP_ENABLED);
        String mode = loadRaw(KEY_IP_MODE);
        String list = loadRaw(KEY_IP_LIST);
        boolean on = "1".equals(enabled);
        List<String> tokens = new ArrayList<>();
        String[] parts = list.split("[,\\s]+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return new IpAccessRule(on, "deny".equals(mode) ? "deny" : "allow", tokens);
    }

    private String loadRaw(String key) {
        List<String> rows = jdbc.query("SELECT config_value FROM sys_config WHERE config_key=?",
                (rs, n) -> rs.getString(1), key);
        return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
    }

    private long deleteByPrefix(RedisConnection connection, String prefix) {
        long deleted = 0;
        List<byte[]> batch = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 500) {
                    deleted += deleteBatch(connection, batch);
                }
            }
        }
        deleted += deleteBatch(connection, batch);
        return deleted;
    }

    private long deleteBatch(RedisConnection connection, List<byte[]> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = connection.del(keys.toArray(new byte[0][]));
        keys.clear();
        return deleted == null ? 0 : deleted;
    }

    private void audit(long operatorId, String operatorName, String module, String action,
                       String target, String detail, String clientIp) {
        try {
            jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,detail,ip) VALUES(?,?,?,?,?,?)",
                    operatorId, operatorName, module, action, target + ": " + detail, clientIp);
        } catch (org.springframework.dao.DataAccessException e) {
            LOGGER.warn("audit log insert failed for {}: {}", action, e.getMessage());
        }
    }

    private void requireContentKey(String contentKey) {
        if (!CONTENT_PRIVACY_POLICY.equals(contentKey) && !CONTENT_USER_AGREEMENT.equals(contentKey)
                && !CONTENT_REGISTER_NOTICE.equals(contentKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的内容类型");
        }
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private String maskIfSecret(KeySpec spec, String value) {
        return spec.secret() ? maskSecret(value) : value;
    }

    public String maskSecret(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static void validatePositiveInt(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 100000) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需为 0-100000 的整数");
        }
    }

    private static void validateRatio(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            java.math.BigDecimal ratio = new java.math.BigDecimal(value);
            if (ratio.signum() >= 0 && ratio.compareTo(java.math.BigDecimal.ONE) <= 0 && ratio.scale() <= 6) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "比例必须为 0-1 之间的小数");
    }

    private static void validateNonNegativeDecimal(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(value);
            if (v.signum() >= 0 && v.scale() <= 2
                    && v.compareTo(new java.math.BigDecimal("100000000")) <= 0) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须为不小于 0 的金额（最多 2 位小数）");
    }

    private static void validatePositiveDecimal(String value) {
        validateNonNegativeDecimal(value);
        if (value.isEmpty() || new java.math.BigDecimal(value).signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金额必须大于 0");
        }
    }

    private static void validateDecimalOrBlank(String value) {
        if (value == null || value.isEmpty() || "NULL".equalsIgnoreCase(value.trim())) {
            return;
        }
        validateNonNegativeDecimal(value);
    }

    private static void validateNonNegativeLong(String value) {
        if (value.isEmpty()) {
            return;
        }
        try {
            long v = Long.parseLong(value);
            if (v >= 0 && v <= 100000000L) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须为 0-100000000 之间的整数");
    }

    private static void validateExtensionList(String value) {
        if (value.isEmpty()) {
            return;
        }
        for (String part : value.split(",")) {
            String ext = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!ext.matches("[a-z0-9]{1,8}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "格式列表需为逗号分隔的扩展名，如 jpg,png");
            }
        }
    }

    private static void validatePlain(String value) {
        if (value.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容过长");
        }
    }

    private static void validateSmsProvider(String value) {
        if (!value.isEmpty() && !value.equals("aliyun") && !value.equals("tencent")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信服务商仅支持 aliyun/tencent，留空为未配置");
        }
    }

    private static void validateCompanyName(String value) {
        if (value.isEmpty() || value.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公司名称需为 1-64 个字符");
        }
    }

    private static void validatePhone(String value) {
        if (!value.isEmpty() && !PHONE.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "电话号码只能包含数字、空格和 + - ( )");
        }
    }

    private static void validateEmail(String value) {
        if (!value.isEmpty() && (value.length() > 64 || !EMAIL.matcher(value).matches())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
    }

    private static void validateQq(String value) {
        if (!value.isEmpty() && !QQ.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QQ 需为 5-20 位数字");
        }
    }

    private static void validateSwitch(String value) {
        if (!"0".equals(value) && !"1".equals(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开关只能是 0 或 1");
        }
    }

    private static void validateWatermarkPosition(String value) {
        if (!List.of("topLeft", "topRight", "bottomLeft", "bottomRight", "tile").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "水印位置不支持");
        }
    }

    private static void validateOpacity(String value) {
        try {
            java.math.BigDecimal opacity = new java.math.BigDecimal(value);
            if (opacity.signum() > 0 && opacity.compareTo(java.math.BigDecimal.ONE) <= 0 && opacity.scale() <= 2) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the shared error below
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不透明度需为 0-1 之间的小数");
    }

    private static void validateWatermarkSize(String value) {
        try {
            int size = Integer.parseInt(value);
            if (size >= 8 && size <= 200) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the shared error below
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字号需为 8-200 之间的整数");
    }

    private static void validateIpMode(String value) {
        if (!"allow".equals(value) && !"deny".equals(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "限制模式只能是 allow 或 deny");
        }
    }

    private static void validateIpList(String value) {
        if (value.isEmpty()) {
            return;
        }
        String[] parts = value.split("[,\\s]+");
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            count++;
            if (!isIpToken(part)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP 列表包含无效条目：" + part);
            }
        }
        if (count > MAX_IP_ENTRIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP 列表最多 " + MAX_IP_ENTRIES + " 条");
        }
    }

    private static boolean isIpToken(String token) {
        if (IPV4.matcher(token).matches()) {
            return isIpv4(token);
        }
        java.util.regex.Matcher cidr = IPV4_CIDR.matcher(token);
        if (cidr.matches()) {
            return isIpv4(token.substring(0, token.indexOf('/')))
                    && Integer.parseInt(token.substring(token.indexOf('/') + 1)) <= 32;
        }
        return token.indexOf(':') > 0 && token.length() <= 45;
    }

    private static boolean isIpv4(String value) {
        String[] octets = value.split("\\.");
        for (String octet : octets) {
            if (octet.length() > 1 && octet.startsWith("0")) {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    public record KeySpec(String group, String label, boolean secret, Validator validator) {
    }

    @FunctionalInterface
    public interface Validator {
        void validate(String value);
    }

    /** Parsed ip.access.* configuration with loopback always allowed as a lock-out safety net. */
    public record IpAccessRule(boolean enabled, String mode, List<String> tokens) {
        public boolean allows(String clientIp) {
            if (!enabled) {
                return true;
            }
            if (clientIp == null || clientIp.isEmpty()) {
                return false;
            }
            if (isLoopback(clientIp)) {
                return true;
            }
            boolean matched = matchesAny(clientIp);
            return "deny".equals(mode) ? !matched : matched;
        }

        private boolean matchesAny(String clientIp) {
            for (String token : tokens) {
                if (token.equals(clientIp)) {
                    return true;
                }
                int slash = token.indexOf('/');
                if (slash > 0 && IPV4.matcher(token.substring(0, slash)).matches() && IPV4.matcher(clientIp).matches()) {
                    int bits = Integer.parseInt(token.substring(slash + 1));
                    if (bits >= 0 && bits <= 32 && sameNetwork(clientIp, token.substring(0, slash), bits)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean sameNetwork(String left, String right, int bits) {
            if (bits == 0) {
                return true;
            }
            long mask = 0xFFFFFFFFL << (32 - bits) & 0xFFFFFFFFL;
            return (toLong(left) & mask) == (toLong(right) & mask);
        }

        private static long toLong(String ipv4) {
            long value = 0;
            for (String octet : ipv4.split("\\.")) {
                value = (value << 8) | Integer.parseInt(octet);
            }
            return value;
        }

        public static boolean isLoopback(String ip) {
            return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
        }
    }
    private static void validateQrUrl(String value) {
        if(value.isEmpty()) return;
        try {var u=java.net.URI.create(value);if(!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null) throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"二维码图片必须使用HTTPS地址");}
    }
}
