package com.macro.mall.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 管理首页运维能力：省市区街道地址库、发送记录、访问统计、数据库管理与备份。
 *
 * 所有表名都先按白名单校验并与 information_schema 核对，避免拼接任意 SQL；
 * 备份内容由本类自己生成，还原也只接受本目录内、由本类命名的 .sql 文件。
 */
@Component
public class SystemOpsService {
    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern SAFE_BACKUP = Pattern.compile("mall_demo-[0-9]{8}-[0-9]{6}-[0-9a-f]{6}[.]sql");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_TABLES_PER_COMMAND = 50;
    private static final int MAX_LIST_SIZE = 200;

    private final JdbcTemplate jdbc;

    @Value("${mall.database.backup-dir:/opt/mall-demo/db-backups}")
    private String configuredDir;

    public SystemOpsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- 地址库 ----------------

    public List<Map<String, Object>> areaChildren(long parentId) {
        return jdbc.queryForList(
                "SELECT id,parent_id,name,level,code,sort,enabled FROM sys_area WHERE parent_id=? ORDER BY sort,id", parentId);
    }

    public List<Map<String, Object>> areaTree() {
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT id,parent_id,name,level,code,sort,enabled FROM sys_area ORDER BY level,sort,id");
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : all) {
            row.put("children", new ArrayList<Map<String, Object>>());
            byId.put(number(row.get("id")), row);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : byId.values()) {
            long parentId = number(row.get("parent_id"));
            Map<String, Object> parent = parentId == 0 ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(row);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(row);
            }
        }
        return roots;
    }

    public long saveArea(Map<String, Object> body) {
        String name = text(body.get("name"));
        if (name.isEmpty() || name.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "地址名称必填，且不超过64个字符");
        }
        String code = text(body.get("code"));
        if (code.length() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行政区划代码不超过16个字符");
        }
        long id = number(body.get("id"));
        long parentId = number(body.get("parentId"));
        int sort = (int) number(body.get("sort"));
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        int level = parentId == 0 ? 1 : requireAreaLevel(parentId) + 1;
        if (level > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "街道下面不能再新增层级");
        }
        if (id > 0) {
            Long parent = jdbc.queryForObject("SELECT parent_id FROM sys_area WHERE id=?", Long.class, id);
            if (parent == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
            }
            if (parent != parentId) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持移动层级，请删除后重建");
            }
            try {
                jdbc.update("UPDATE sys_area SET name=?,code=?,sort=?,enabled=? WHERE id=?", name, blankToNull(code), sort, enabled ? 1 : 0, id);
            } catch (DuplicateKeyException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一级下已存在同名地址");
            }
            return id;
        }
        try {
            jdbc.update("INSERT INTO sys_area(parent_id,name,level,code,sort,enabled) VALUES(?,?,?,?,?,?)",
                    parentId, name, level, blankToNull(code), sort, enabled ? 1 : 0);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一级下已存在同名地址");
        }
        Long created = jdbc.queryForObject(
                "SELECT id FROM sys_area WHERE parent_id=? AND name=? ORDER BY id DESC LIMIT 1", Long.class, parentId, name);
        return created == null ? 0 : created;
    }

    public void deleteArea(long id) {
        Long children = jdbc.queryForObject("SELECT COUNT(*) FROM sys_area WHERE parent_id=?", Long.class, id);
        if (children != null && children > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先删除下级地址");
        }
        if (jdbc.update("DELETE FROM sys_area WHERE id=?", id) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
    }

    private int requireAreaLevel(long id) {
        List<Integer> levels = jdbc.queryForList("SELECT level FROM sys_area WHERE id=?", Integer.class, id);
        if (levels.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级地址不存在");
        }
        return levels.get(0);
    }

    // ---------------- 发送记录 ----------------

    public Map<String, Object> messageRecords(int pageNum, int pageSize, String channel, String status) {
        int num = Math.max(1, pageNum);
        int size = Math.min(MAX_LIST_SIZE, Math.max(1, pageSize));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!text(channel).isEmpty()) {
            where.append(" AND channel=?");
            args.add(channel.trim());
        }
        if (!text(status).isEmpty()) {
            where.append(" AND status=?");
            args.add(status.trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_message_record" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((num - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT id,channel,target,content,biz_type,biz_id,status,error_msg,create_time FROM sys_message_record"
                        + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pageNum", num);
        result.put("pageSize", size);
        result.put("total", total == null ? 0L : total);
        result.put("list", list);
        return result;
    }

    public List<Map<String, Object>> messageSummary(int days) {
        int window = windowDays(days);
        return jdbc.queryForList(
                "SELECT channel, status, COUNT(*) AS amount FROM sys_message_record "
                        + "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) GROUP BY channel, status ORDER BY channel, status", window);
    }

    // ---------------- 访问统计 ----------------

    public Map<String, Object> visitSummary(int days) {
        int window = windowDays(days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", window);
        result.put("totalVisits", jdbc.queryForObject("SELECT COUNT(*) FROM sys_visit_log", Long.class));
        result.put("todayVisits", jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_visit_log WHERE create_time >= CURDATE()", Long.class));
        result.put("todayVisitors", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT visitor_key) FROM sys_visit_log WHERE create_time >= CURDATE()", Long.class));
        result.put("daily", jdbc.queryForList(
                "SELECT DATE(create_time) AS statDate, client, COUNT(*) AS visits, COUNT(DISTINCT visitor_key) AS visitors "
                        + "FROM sys_visit_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
                        + "GROUP BY DATE(create_time), client ORDER BY statDate DESC, client", window));
        result.put("topPaths", jdbc.queryForList(
                "SELECT path, COUNT(*) AS visits FROM sys_visit_log WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
                        + "GROUP BY path ORDER BY visits DESC, path LIMIT 20", window));
        result.put("recent", jdbc.queryForList(
                "SELECT client, path, method, ip, member_id, create_time FROM sys_visit_log ORDER BY id DESC LIMIT 50"));
        return result;
    }

    // ---------------- 数据库管理 ----------------

    public List<Map<String, Object>> tables(String keyword) {
        return jdbc.queryForList(
                "SELECT TABLE_NAME AS tableName, ENGINE AS engine, TABLE_ROWS AS tableRows, "
                        + "ROUND((DATA_LENGTH+INDEX_LENGTH)/1024/1024,2) AS sizeMb, DATA_FREE AS freeBytes, "
                        + "TABLE_COMMENT AS tableComment, CREATE_TIME AS createTime, UPDATE_TIME AS updateTime "
                        + "FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE=? AND TABLE_NAME LIKE ? ORDER BY TABLE_NAME",
                databaseName(), "BASE TABLE", "%" + text(keyword).trim() + "%");
    }

    public Map<String, Object> tableStructure(String table) {
        String name = validateTable(table);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", name);
        result.put("createSql", jdbc.queryForObject("SHOW CREATE TABLE `" + name + "`", (rs, rowNum) -> rs.getString(2)));
        result.put("columns", jdbc.queryForList(
                "SELECT COLUMN_NAME AS columnName, COLUMN_TYPE AS columnType, IS_NULLABLE AS nullable, "
                        + "COLUMN_KEY AS columnKey, COLUMN_DEFAULT AS columnDefault, EXTRA AS extra, COLUMN_COMMENT AS columnComment "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",
                databaseName(), name));
        result.put("indexes", jdbc.queryForList(
                "SELECT INDEX_NAME AS indexName, NON_UNIQUE AS nonUnique, SEQ_IN_INDEX AS seqInIndex, COLUMN_NAME AS columnName "
                        + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY INDEX_NAME, SEQ_IN_INDEX",
                databaseName(), name));
        return result;
    }

    public List<Map<String, Object>> tableCommand(String action, List<String> tables) {
        String command = text(action).toUpperCase();
        if (!List.of("OPTIMIZE", "REPAIR", "ANALYZE", "CHECK").contains(command)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 OPTIMIZE/REPAIR/ANALYZE/CHECK");
        }
        List<String> names = validateTables(tables);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableName", name);
            row.put("command", command);
            try {
                row.put("messages", jdbc.queryForList(command + " TABLE `" + name + "`"));
                row.put("ok", true);
            } catch (RuntimeException e) {
                row.put("ok", false);
                row.put("error", e.getMessage());
            }
            result.add(row);
        }
        return result;
    }

    public Path backupDir() {
        Path configured = Paths.get(text(configuredDir).isEmpty()
                ? "/opt/mall-demo/db-backups" : configuredDir.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configured);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "持久备份目录不可用: " + e.getMessage());
        }
        if (!Files.isWritable(configured)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "持久备份目录不可写: " + configured);
        }
        return configured;
    }

    public String createBackup() {
        Path dir = backupDir();
        String fileName = "mall_demo-" + LocalDateTime.now().format(STAMP) + "-"
                + HexFormat.of().formatHex(randomBytes(3)) + ".sql";
        Path target = dir.resolve(fileName);
        String database = databaseName();
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_TYPE=? ORDER BY TABLE_NAME",
                String.class, database, "BASE TABLE");
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("-- mall demo backup " + LocalDateTime.now() + System.lineSeparator());
            writer.write("SET NAMES utf8mb4;" + System.lineSeparator());
            writer.write("SET FOREIGN_KEY_CHECKS=0;" + System.lineSeparator());
            for (String table : tables) {
                writeTable(writer, table);
            }
            writer.write("SET FOREIGN_KEY_CHECKS=1;" + System.lineSeparator());
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "备份写入失败: " + e.getMessage());
        }
        return fileName;
    }

    private void writeTable(Writer writer, String table) throws IOException {
        String name = validateTable(table);
        writer.write(System.lineSeparator() + "-- table " + name + System.lineSeparator());
        writer.write("DROP TABLE IF EXISTS `" + name + "`;" + System.lineSeparator());
        writer.write(jdbc.queryForObject("SHOW CREATE TABLE `" + name + "`", (rs, rowNum) -> rs.getString(2)) + ";");
        writer.write(System.lineSeparator());
        jdbc.query("SELECT * FROM `" + name + "`", (ResultSetExtractor<Void>) rs -> {
            try {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    StringBuilder sql = new StringBuilder("INSERT INTO `").append(name).append("` VALUES (");
                    for (int i = 1; i <= columns; i++) {
                        if (i > 1) {
                            sql.append(",");
                        }
                        sql.append(sqlLiteral(rs.getObject(i)));
                    }
                    sql.append(");");
                    writer.write(sql.toString());
                    writer.write(System.lineSeparator());
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public List<Map<String, Object>> listBackups() {
        Path dir = backupDir();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> SAFE_BACKUP.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .map(path -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", path.getFileName().toString());
                        try {
                            row.put("sizeBytes", Files.size(path));
                            row.put("modifiedAt", Files.getLastModifiedTime(path).toInstant().toString());
                        } catch (IOException ignored) {
                            row.put("sizeBytes", 0L);
                        }
                        return row;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "备份目录不可用: " + e.getMessage());
        }
    }

    public Path resolveBackup(String name) {
        String fileName = text(name).trim();
        if (!SAFE_BACKUP.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备份文件名不合法");
        }
        Path dir = backupDir();
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "备份文件不存在");
        }
        return target;
    }

    public void deleteBackup(String name) {
        Path target = resolveBackup(name);
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 还原：先自动做一次还原前备份，再逐条执行备份文件里的语句。
     * 只接受本目录内由本类命名的备份文件，文件名在进入这里之前已经过白名单校验。
     */
    public Map<String, Object> restoreBackup(String name) {
        Path source = resolveBackup(name);
        String safetyBackup = createBackup();
        int executed = 0;
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            StringBuilder statement = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("--")) {
                    continue;
                }
                statement.append(line).append(System.lineSeparator());
                if (line.endsWith(";")) {
                    String sql = statement.toString().trim();
                    statement.setLength(0);
                    if (!sql.isEmpty()) {
                        jdbc.execute(sql);
                        executed++;
                    }
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取备份失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restoredFrom", name);
        result.put("safetyBackup", safetyBackup);
        result.put("statements", executed);
        return result;
    }

    // ---------------- 工具方法 ----------------

    private String databaseName() {
        String name = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (name == null || name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法确定当前数据库");
        }
        return name;
    }

    String validateTable(String table) {
        String name = text(table).trim();
        if (!SAFE_TABLE.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表名不合法");
        }
        List<String> found = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",
                String.class, databaseName(), name);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "表不存在: " + name);
        }
        return name;
    }

    private List<String> validateTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要操作的表");
        }
        if (tables.size() > MAX_TABLES_PER_COMMAND) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "一次最多处理 " + MAX_TABLES_PER_COMMAND + " 张表");
        }
        List<String> result = new ArrayList<>();
        for (String table : tables) {
            String name = validateTable(table);
            if (!result.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private static String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return "0x" + HexFormat.of().formatHex(bytes);
        }
        String raw = value instanceof java.sql.Timestamp || value instanceof java.sql.Date
                ? value.toString()
                : String.valueOf(value);
        return "\u0027" + escape(raw) + "\u0027";
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\u0027", "\u0027\u0027")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u0000", "\\0");
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static int windowDays(int days) {
        return Math.min(180, Math.max(1, days));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static long number(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数值参数不合法: " + text);
        }
    }
}
