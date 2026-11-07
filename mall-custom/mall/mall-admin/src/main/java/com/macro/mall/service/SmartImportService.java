package com.macro.mall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFPicture;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能导入：不要求固定表头。自动定位表头行、按列名猜列用途，
 * 表内分类自动映射/创建，价格按专区规则补齐，图片支持链接列与内嵌图片。
 */
@Service
public class SmartImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmartImportService.class);
    private static final Path IMAGE_DIR = Path.of(System.getProperty("mall.product-images", "/opt/mall-demo/product-images"));
    private static final int MAX_PRODUCTS = 2000;
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal PICK_PRICE = new BigDecimal("3999.00");

    public enum Role { NAME, SUBTITLE, SN, PRICE, STOCK, PIC, CATEGORY, SUMMARY, DETAIL, BRAND, IGNORE }

    /** 列识别结果，回传给后台确认/修改。 */
    public record Column(int index, String title, String role, int confidence, List<String> samples) {}

    /** 预览用的样例行。 */
    public record Mapped(String name, String price, String category, String subtitle, String sn, String stock, String pic) {}

    public record Preview(String sheet, int headerRow, int totalRows, String zone, List<Column> columns,
                          List<Mapped> samples, List<String> notes) {}

    private static final Map<Role, Map<String, Integer>> ALIASES = aliases();

    private static Map<Role, Map<String, Integer>> aliases() {
        Map<Role, Map<String, Integer>> map = new LinkedHashMap<>();
        map.put(Role.NAME, of("商品名称", 100, "产品名称", 100, "商品名", 95, "产品名", 95, "名称", 88, "品名", 90,
                "商品标题", 90, "标题", 72, "款名", 80, "商品", 60));
        map.put(Role.SUBTITLE, of("副标题", 100, "商品副标题", 100, "卖点", 84, "商品卖点", 88, "短描述", 78,
                "简述", 72, "宣传语", 70));
        map.put(Role.SN, of("商品货号", 100, "货号", 100, "商品编号", 95, "产品编号", 95, "商品编码", 92,
                "产品型号", 95, "型号", 88, "款号", 90, "sku", 86, "条码", 72, "编码", 70, "编号", 78));
        map.put(Role.PRICE, of("售价", 100, "销售价", 98, "零售价", 96, "平台价", 94, "京东价", 94, "商品价格", 92,
                "价格", 88, "单价", 82, "现价", 86, "促销价", 80));
        map.put(Role.STOCK, of("库存", 100, "商品库存", 100, "可售库存", 92, "存货", 84, "数量", 68, "剩余库存", 86));
        map.put(Role.PIC, of("封面图url", 100, "图片url", 100, "图片链接", 96, "商品图片", 96, "产品图片", 96,
                "主图", 94, "封面图", 92, "封面", 86, "产品图", 90, "图片", 88, "图", 60));
        map.put(Role.CATEGORY, of("商品分类", 100, "商品类别", 96, "三级类目", 96, "类目", 90, "分类", 92,
                "类别", 90, "品类", 88, "专区", 78, "所属分类", 90));
        map.put(Role.SUMMARY, of("商品简介", 100, "简介", 90, "行程简介", 96, "商品描述", 88, "描述", 78,
                "产品信息", 80, "卖点简介", 74));
        map.put(Role.DETAIL, of("商品详情", 100, "行程详情", 96, "详情", 90, "商品介绍", 90, "详情页", 84,
                "信息整合", 86, "产品特点", 78, "介绍", 78));
        map.put(Role.BRAND, of("品牌", 100, "商品品牌", 100, "所属品牌", 92));
        return map;
    }

    private static Map<String, Integer> of(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], (Integer) pairs[i + 1]);
        return map;
    }

    private final PickProductImportService importer;

    public SmartImportService(PickProductImportService importer) {
        this.importer = importer;
    }

    /** 预览：识别表头行与列用途，返回样例，不写库。 */
    public Preview preview(String zone, MultipartFile file) throws IOException {
        validateZone(zone);
        Table table = read(zone, file, null);
        Map<Role, Integer> columns = table.columns;
        List<Column> list = new ArrayList<>();
        for (int i = 0; i < table.headers.size(); i++) {
            String title = table.headers.get(i);
            Role role = columnRole(columns, i);
            list.add(new Column(i, title, role.name(), role == Role.IGNORE ? 0 : 100, samples(table, i)));
        }
        List<Mapped> samples = new ArrayList<>();
        for (int i = 0; i < Math.min(5, table.rows.size()); i++) {
            List<String> row = table.rows.get(i);
            String name = value(row, columns, Role.NAME);
            if (name.isBlank()) continue;
            samples.add(new Mapped(name, "PICK".equals(zone) ? "3999" : price(zone, row, columns).map(BigDecimal::toPlainString).orElse(""),
                    category(zone, table, row, columns), subtitle(row, columns, name), value(row, columns, Role.SN),
                    String.valueOf(stock(row, columns)), picture(row, columns, table, i)));
        }
        List<String> notes = new ArrayList<>(table.notes);
        notes.add(0, "工作表「" + table.sheet + "」第 " + (table.firstRow + table.headerRow + 1) + " 行识别为表头，共 " + table.rows.size() + " 行数据");
        return new Preview(table.sheet, table.firstRow + table.headerRow, table.rows.size(), zone, list, samples, notes);
    }

    /** 导入：可带前端确认后的列映射（columns: 列序号 → 用途）。 */
    public PickProductImportService.Result importFile(String zone, MultipartFile file, String mappingJson) throws IOException {
        validateZone(zone);
        Table table = read(zone, file, mappingJson);
        Map<Role, Integer> columns = table.columns;
        if (!columns.containsKey(Role.NAME)) bad("没有识别到商品名称列，请在预览里把对应列改为「商品名称」后再导入");
        List<PickProductImportService.ProductRow> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> sns = new HashSet<>();
        int noImage = 0;
        for (int i = 0; i < table.rows.size(); i++) {
            List<String> row = table.rows.get(i);
            int line = table.dataStart + i + 1;
            String name = value(row, columns, Role.NAME);
            if (name.isBlank()) continue;
            if (name.length() > 140) {
                skipped.add("第" + line + "行商品名称超过140字，已跳过：" + name.substring(0, 30) + "…");
                continue;
            }
            BigDecimal price;
            if ("PICK".equals(zone)) {
                price = PICK_PRICE;
            } else {
                price = price(zone, row, columns).orElse(null);
                if (price == null) {
                    skipped.add("第" + line + "行没有可识别的价格，已跳过：" + name);
                    continue;
                }
            }
            String sn = value(row, columns, Role.SN).trim();
            if (sn.length() > 64) sn = sn.substring(0, 64);
            if (!sn.isBlank() && !sns.add(sn)) {
                skipped.add("第" + line + "行货号与本文件其他行重复，已跳过：" + name);
                continue;
            }
            String pic = picture(row, columns, table, i);
            if (pic.isBlank()) noImage++;
            String category = category(zone, table, row, columns);
            String subtitle = subtitle(row, columns, name);
            String summary = value(row, columns, Role.SUMMARY);
            String detail = value(row, columns, Role.DETAIL);
            if (summary.isBlank()) summary = detail;
            if (detail.isBlank()) detail = summary;
            if (summary.isBlank()) summary = name;
            if (detail.isBlank()) detail = name;
            if (summary.length() > 500) summary = summary.substring(0, 500);
            if (detail.length() > 10000) detail = detail.substring(0, 10000);
            items.add(new PickProductImportService.ProductRow(line, category, name, subtitle, sn,
                    stock(row, columns), pic, summary, detail, price));
        }
        if (items.isEmpty()) {
            String why = skipped.isEmpty() ? "没有识别到商品行" : String.join("；", skipped.subList(0, Math.min(3, skipped.size())));
            bad("没有可导入的商品：" + why);
        }
        if (items.size() > MAX_PRODUCTS) bad("每次最多导入" + MAX_PRODUCTS + "个商品，请拆分文件");
        if (noImage > 0) skipped.add("有 " + noImage + " 行没有图片，已导入但商城前台无图，可逐个上传后再上架");
        LOGGER.info("智能导入 zone={} file={} sheet={} header={} rows={} items={} skipped={}", zone,
                file.getOriginalFilename(), table.sheet, table.headerRow + 1, table.rows.size(), items.size(), skipped.size());
        return importer.createAll(zone, items, skipped, true, true);
    }

    private static void validateZone(String zone) {
        if (!List.of("PICK", "REBUY", "CONSUME").contains(zone)) bad("无效的商品专区");
    }

    private static Role columnRole(Map<Role, Integer> columns, int index) {
        for (var e : columns.entrySet()) if (e.getValue() == index) return e.getKey();
        return Role.IGNORE;
    }

    private static List<String> samples(Table table, int index) {
        List<String> values = new ArrayList<>();
        for (List<String> row : table.rows) {
            if (index >= row.size()) continue;
            String v = row.get(index).trim();
            if (v.isBlank() || values.contains(v)) continue;
            values.add(v.length() > 40 ? v.substring(0, 40) + "…" : v);
            if (values.size() >= 3) break;
        }
        return values;
    }

    private static String value(List<String> row, Map<Role, Integer> columns, Role role) {
        Integer index = columns.get(role);
        if (index == null || index >= row.size()) return "";
        String value = row.get(index);
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static java.util.Optional<BigDecimal> price(String zone, List<String> row, Map<Role, Integer> columns) {
        Integer index = columns.get(Role.PRICE);
        if (index == null || index >= row.size()) return java.util.Optional.empty();
        String raw = row.get(index).replace(",", "").replace("¥", "").replace("￥", "").replace("元", "").trim();
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find()) return java.util.Optional.empty();
        try {
            BigDecimal value = new BigDecimal(matcher.group(1));
            if (value.signum() <= 0 || value.compareTo(new BigDecimal("999999.99")) > 0) return java.util.Optional.empty();
            return java.util.Optional.of(value);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static String category(String zone, Table table, List<String> row, Map<Role, Integer> columns) {
        if ("PICK".equals(zone)) return "旅游路线";
        String value = value(row, columns, Role.CATEGORY);
        if (value.isBlank()) value = table.sheet;
        if (value.isBlank()) value = "未分类";
        value = value.replaceAll("[\\r\\n]+", " ").trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String subtitle(List<String> row, Map<Role, Integer> columns, String name) {
        String value = value(row, columns, Role.SUBTITLE);
        if (value.isBlank()) value = value(row, columns, Role.BRAND);
        if (value.isBlank()) value = name;
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static int stock(List<String> row, Map<Role, Integer> columns) {
        String raw = value(row, columns, Role.STOCK);
        Matcher matcher = NUMBER.matcher(raw.replace(",", ""));
        if (!matcher.find()) return 100;
        try {
            int value = (int) Double.parseDouble(matcher.group(1));
            return Math.max(1, Math.min(100000, value));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static String picture(List<String> row, Map<Role, Integer> columns, Table table, int rowIndex) {
        Integer index = columns.get(Role.PIC);
        if (index != null && index < row.size()) {
            String raw = row.get(index).trim();
            if (raw.startsWith("//")) raw = "https:" + raw;
            if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("/product-images/")) return raw;
            if (raw.contains("DISPIMG")) {
                for (var e : table.cellImages.entrySet()) if (raw.contains(e.getKey())) return e.getValue();
            }
        }
        String embedded = table.rowImages.get(table.dataStart + rowIndex);
        return embedded == null ? "" : embedded;
    }

    // ---------------------------------------------------------------- parsing

    private static final class Table {
        String sheet;
        int headerRow;
        int firstRow;
        int dataStart;
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        Map<Role, Integer> columns = new LinkedHashMap<>();
        Map<Integer, String> rowImages = new HashMap<>();
        Map<String, String> cellImages = new HashMap<>();
        List<String> notes = new ArrayList<>();
    }

    private Table read(String zone, MultipartFile file, String mappingJson) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 400L * 1024 * 1024) bad("文件不能为空且不能超过400MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String wantedSheet = null;
        int wantedHeader = -1;
        if (mappingJson != null && !mappingJson.isBlank()) {
            try {
                JsonNode root = JSON.readTree(mappingJson);
                wantedSheet = root.path("sheet").asText(null);
                wantedHeader = root.path("headerRow").asInt(-1);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "列映射格式不正确", e);
            }
        }
        if (filename.endsWith(".csv")) {
            Table table = new Table();
            List<List<String>> grid = PickProductImportService.parseCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
            if (grid.isEmpty()) bad("文件没有内容");
            table.sheet = "CSV";
            table.firstRow = 0;
            int header = wantedHeader >= 0 && wantedHeader < grid.size() ? wantedHeader : detectHeader(grid);
            if (header < 0) bad("没有识别到商品名称列：表头需包含「商品名称」「产品名称」「名称」等字样");
            table.headerRow = header;
            table.dataStart = header + 1;
            table.headers = padded(grid.get(header));
            table.rows = new ArrayList<>(grid.subList(header + 1, grid.size()));
            table.columns = mapping(zone, table.headers, mappingJson);
            return table;
        }
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) bad("仅支持 .csv、.xlsx、.xls 文件");
        Path temp = Files.createTempFile("smart-import-", filename.endsWith(".xls") ? ".xls" : ".xlsx");
        file.transferTo(temp);
        try (Workbook workbook = WorkbookFactory.create(temp.toFile())) {
            if (workbook.getNumberOfSheets() < 1) bad("Excel 没有工作表");
            Table best = null;
            for (Sheet sheet : workbook) {
                if (wantedSheet != null && !wantedSheet.isBlank() && !wantedSheet.equals(sheet.getSheetName())) continue;
                Table table = readSheet(zone, sheet, temp, mappingJson, wantedHeader);
                if (table == null) continue;
                if (best == null || table.columns.size() > best.columns.size()
                        || (table.columns.size() == best.columns.size() && table.rows.size() > best.rows.size())) best = table;
            }
            if (best == null) bad("没有找到可识别的表格：请确认文件里有商品名称列");
            return best;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件无法读取，请确认是有效的 Excel 或 CSV", e);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Table readSheet(String zone, Sheet sheet, Path source, String mappingJson, int wantedHeader) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        int first = Math.max(0, sheet.getFirstRowNum());
        int last = sheet.getLastRowNum();
        if (last < first) return null;
        List<List<String>> grid = new ArrayList<>();
        for (int r = first; r <= last; r++) {
            Row row = sheet.getRow(r);
            List<String> values = new ArrayList<>();
            int cells = row == null ? 0 : Math.max(row.getLastCellNum(), 0);
            for (int c = 0; c < cells; c++) {
                Cell cell = row == null ? null : row.getCell(c);
                if (cell == null) { values.add(""); continue; }
                String text;
                if (cell.getCellType() == CellType.FORMULA) {
                    String formula = cell.getCellFormula();
                    text = formula.contains("DISPIMG") ? formula : formatter.formatCellValue(cell);
                } else {
                    text = formatter.formatCellValue(cell);
                }
                values.add(text == null ? "" : text.trim());
            }
            grid.add(values);
        }
        int header = wantedHeader >= 0 && wantedHeader >= first && wantedHeader <= last ? wantedHeader - first : detectHeader(grid);
        if (header < 0) return null;
        Table table = new Table();
        table.sheet = sheet.getSheetName();
        table.firstRow = first;
        table.headerRow = header;
        table.dataStart = first + header + 1;
        table.headers = padded(grid.get(header));
        table.rows = new ArrayList<>(grid.subList(header + 1, grid.size()));
        table.columns = mapping(zone, table.headers, mappingJson);
        try {
            table.cellImages.putAll(BrandWorkbookReader.readCellImages(source, IMAGE_DIR));
        } catch (Exception e) {
            LOGGER.debug("读取单元格图片失败 sheet={}", sheet.getSheetName(), e);
        }
        collectPictures(sheet, table.rowImages);
        return table;
    }

    private static List<String> padded(List<String> row) {
        List<String> values = new ArrayList<>(row);
        while (!values.isEmpty() && values.get(values.size() - 1).isBlank()) values.remove(values.size() - 1);
        return values;
    }

    /** 在前 15 行里找最像表头的一行：要求能识别出商品名称，且识别出的列最多。 */
    private static int detectHeader(List<List<String>> grid) {
        int best = -1;
        int bestRoles = 1;
        for (int r = 0; r < Math.min(15, grid.size()); r++) {
            List<String> row = grid.get(r);
            if (row.stream().allMatch(String::isBlank)) continue;
            Map<Role, Integer> columns = mapping(null, padded(row), null);
            if (!columns.containsKey(Role.NAME)) continue;
            if (columns.size() > bestRoles) {
                bestRoles = columns.size();
                best = r;
            }
        }
        return best;
    }

    private static int score(String header, String alias) {
        String h = normalize(header);
        String a = normalize(alias);
        if (h.isEmpty() || a.isEmpty()) return 0;
        if (h.equals(a)) return 100;
        if (h.contains(a)) return a.length() >= 3 ? 84 : 74;
        if (a.contains(h) && h.length() >= 2) return 66;
        return 0;
    }

    static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replace('\u3000', ' ')
                .replaceAll("[\\s*:：()（）\\[\\]【】<>《》\"'`·、,，.。/\\\\|_-]+", "");
    }

    private static int roleScore(Role role, String header) {
        int best = 0;
        for (var alias : ALIASES.get(role).entrySet()) {
            int match = score(header, alias.getKey());
            if (match == 0) continue;
            best = Math.max(best, (int) Math.round(alias.getValue() * match / 100.0));
        }
        return best;
    }

    /** 列名 → 用途。mappingJson 里的 columns 覆盖自动识别结果（前端确认/修改后回传）。 */
    private static Map<Role, Integer> mapping(String zone, List<String> headers, String mappingJson) {
        Map<Role, Integer> overrides = new LinkedHashMap<>();
        Set<Integer> used = new LinkedHashSet<>();
        if (mappingJson != null && !mappingJson.isBlank()) {
            try {
                JsonNode columns = JSON.readTree(mappingJson).path("columns");
                var fields = columns.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    int index = Integer.parseInt(entry.getKey());
                    Role role = Role.valueOf(entry.getValue().asText().toUpperCase(Locale.ROOT));
                    if (role == Role.IGNORE || index < 0 || index >= headers.size()) continue;
                    overrides.put(role, index);
                    used.add(index);
                }
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "列映射格式不正确", e);
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (used.contains(i) || headers.get(i).isBlank()) continue;
            for (Role role : Role.values()) {
                if (role == Role.IGNORE || overrides.containsKey(role)) continue;
                int value = roleScore(role, headers.get(i));
                if (value >= 55) candidates.add(new Candidate(role, i, value));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingInt(Candidate::index));
        Map<Role, Integer> result = new LinkedHashMap<>(overrides);
        Set<Role> taken = new LinkedHashSet<>(overrides.keySet());
        for (Candidate candidate : candidates) {
            if (taken.contains(candidate.role()) || used.contains(candidate.index())) continue;
            result.put(candidate.role(), candidate.index());
            taken.add(candidate.role());
            used.add(candidate.index());
        }
        // 同一张表里有多列价格时，按专区规则挑一列：复购区 平台价→京东价→零售价，消费区 零售价→平台价→京东价
        if (zone != null && !overrides.containsKey(Role.PRICE)) {
            List<String> priority = switch (zone) {
                case "REBUY" -> List.of("平台价", "京东价", "零售价", "销售价", "售价", "现价", "价格", "单价");
                case "CONSUME" -> List.of("零售价", "平台价", "京东价", "销售价", "售价", "现价", "价格", "单价");
                default -> List.of("售价", "销售价", "现价", "零售价", "平台价", "京东价", "价格", "单价");
            };
            Integer chosen = null;
            for (String key : priority) {
                for (int i = 0; i < headers.size(); i++) {
                    if (normalize(headers.get(i)).contains(normalize(key)) && roleScore(Role.PRICE, headers.get(i)) >= 55) {
                        chosen = i;
                        break;
                    }
                }
                if (chosen != null) break;
            }
            if (chosen != null) {
                for (var e : new ArrayList<>(result.entrySet())) {
                    if (e.getKey() == Role.PRICE && !e.getValue().equals(chosen)) result.remove(Role.PRICE);
                }
                result.put(Role.PRICE, chosen);
            }
        }
        return result;
    }

    private record Candidate(Role role, int index, int score) {}

    private static void collectPictures(Sheet sheet, Map<Integer, String> byRow) {
        var drawing = sheet.getDrawingPatriarch();
        if (drawing == null) return;
        List<Shape> shapes = new ArrayList<>();
        if (drawing instanceof org.apache.poi.xssf.usermodel.XSSFDrawing xssf) {
            shapes.addAll(xssf.getShapes());
        } else if (drawing instanceof org.apache.poi.hssf.usermodel.HSSFPatriarch hssf) {
            shapes.addAll(hssf.getChildren());
        }
        for (Shape shape : shapes) {
            try {
                if (shape instanceof XSSFPicture picture) {
                    if (picture.getPictureData() == null) continue;
                    byRow.putIfAbsent(picture.getClientAnchor().getRow1(), BrandWorkbookReader.save(
                            picture.getPictureData().getData(), picture.getPictureData().suggestFileExtension(), IMAGE_DIR));
                } else if (shape instanceof HSSFPicture picture) {
                    if (picture.getPictureData() == null) continue;
                    byRow.putIfAbsent(picture.getClientAnchor().getRow1(), BrandWorkbookReader.save(
                            picture.getPictureData().getData(), picture.getPictureData().suggestFileExtension(), IMAGE_DIR));
                }
            } catch (Exception e) {
                LOGGER.debug("保存内嵌图片失败", e);
            }
        }
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
