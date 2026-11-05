package com.macro.mall.service;

import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.model.PmsSkuStock;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Excel/CSV 导入自选区旅游路线，整批校验、整批提交，默认下架供运营复核。 */
@Service
public class PickProductImportService {
    private static final BigDecimal PRICE = new BigDecimal("3999.00");
    private static final List<String> HEADERS = List.of("商品名称", "副标题", "商品货号", "库存", "封面图URL", "行程简介", "行程详情");
    private final JdbcTemplate jdbc;
    private final PmsProductService productService;

    public PickProductImportService(JdbcTemplate jdbc, PmsProductService productService) {
        this.jdbc = jdbc;
        this.productService = productService;
    }

    public record Result(int imported, List<String> names) {}
    private record Route(int row, String name, String subtitle, String sn, int stock,
                         String cover, String summary, String itinerary) {}

    @Transactional(rollbackFor = Exception.class)
    public Result importFile(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            bad("文件不能为空且不能超过10MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> rows;
        if (filename.endsWith(".csv")) {
            rows = parseCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            rows = new ArrayList<>();
            try (var workbook = WorkbookFactory.create(file.getInputStream())) {
                if (workbook.getNumberOfSheets() < 1) bad("Excel 没有工作表");
                var sheet = workbook.getSheetAt(0);
                if (sheet.getLastRowNum() > 200) bad("每次最多导入200行");
                var formatter = new DataFormatter(Locale.CHINA);
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < HEADERS.size(); i++) {
                        Cell cell = row.getCell(i);
                        if (cell != null && cell.getCellType() == CellType.FORMULA) bad("第" + (row.getRowNum() + 1) + "行含公式，请先粘贴为值");
                        values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    while (rows.size() < row.getRowNum()) rows.add(List.of());
                    rows.add(values);
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件无法读取，请使用下载的模板", e);
            }
        } else {
            bad("仅支持 .csv、.xlsx、.xls 文件");
            return null;
        }
        List<Route> routes = validate(rows);
        List<Long> category = jdbc.queryForList("SELECT id FROM pms_product_category WHERE parent_id=0 AND name='旅游路线' ORDER BY id LIMIT 1", Long.class);
        if (category.isEmpty()) bad("缺少旅游路线商品分类，请先在后台建立分类");
        List<Long> attribute = jdbc.queryForList("SELECT id FROM pms_product_attribute_category WHERE name='旅游路线' ORDER BY id LIMIT 1", Long.class);
        List<String> names = new ArrayList<>();
        for (Route route : routes) {
            PmsProductParam product = new PmsProductParam();
            product.setZoneCode("PICK");
            product.setProductCategoryId(category.get(0));
            product.setProductCategoryName("旅游路线");
            if (!attribute.isEmpty()) product.setProductAttributeCategoryId(attribute.get(0));
            product.setName(route.name());
            product.setSubTitle(route.subtitle());
            product.setProductSn(route.sn().isBlank() ? "TOUR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT) : route.sn());
            product.setStock(route.stock());
            product.setLowStock(0);
            product.setPrice(PRICE);
            product.setOriginalPrice(PRICE);
            product.setPic(route.cover());
            product.setDescription(route.summary());
            String detail = "<p>" + HtmlUtils.htmlEscape(route.itinerary()).replace("\n", "<br>") + "</p>";
            product.setDetailHtml(detail);
            product.setDetailMobileHtml(detail);
            product.setUnit("条");
            product.setPublishStatus(0);
            product.setDeleteStatus(0);
            product.setVerifyStatus(1);
            product.setPromotionType(0);
            product.setGiftPoint(0);
            product.setGiftGrowth(0);
            product.setUsePointLimit(0);
            PmsSkuStock sku = new PmsSkuStock();
            sku.setPrice(PRICE);
            sku.setStock(route.stock());
            sku.setLowStock(0);
            sku.setLockStock(0);
            sku.setSpData("[]");
            product.setSkuStockList(List.of(sku));
            productService.create(product);
            names.add(route.name());
        }
        return new Result(names.size(), names);
    }

    private List<Route> validate(List<List<String>> rows) {
        if (rows.isEmpty()) bad("文件没有表头");
        Map<String, Integer> columns = new HashMap<>();
        List<String> first = rows.get(0);
        for (int i = 0; i < first.size(); i++) columns.put(first.get(i).replace("\uFEFF", "").trim(), i);
        for (String header : HEADERS) if (!columns.containsKey(header)) bad("缺少列：" + header);
        List<Route> result = new ArrayList<>();
        Set<String> sns = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(String::isBlank)) continue;
            int line = i + 1;
            String name = value(row, columns, "商品名称");
            String subtitle = value(row, columns, "副标题");
            String sn = value(row, columns, "商品货号");
            String stockText = value(row, columns, "库存");
            String cover = value(row, columns, "封面图URL");
            String summary = value(row, columns, "行程简介");
            String detail = value(row, columns, "行程详情");
            if (name.length() < 2 || name.length() > 140 || subtitle.isBlank() || subtitle.length() > 255)
                bad("第" + line + "行：商品名称或副标题无效");
            if (sn.length() > 64 || (!sn.isBlank() && !sns.add(sn))) bad("第" + line + "行：商品货号重复或过长");
            if (!sn.isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM pms_product WHERE product_sn=?", Integer.class, sn) > 0)
                bad("第" + line + "行：商品货号已存在");
            int stock;
            try { stock = Integer.parseInt(stockText); } catch (NumberFormatException e) { bad("第" + line + "行：库存必须为正整数"); return result; }
            if (stock < 1 || stock > 100000) bad("第" + line + "行：库存范围为1至100000");
            if (cover.length() > 255 || !(cover.startsWith("https://") || cover.startsWith("/product-images/")))
                bad("第" + line + "行：封面图须为 HTTPS 或后台上传的图片地址");
            if (summary.isBlank() || summary.length() > 500 || detail.isBlank() || detail.length() > 10000)
                bad("第" + line + "行：行程简介或详情为空、过长");
            result.add(new Route(line, name, subtitle, sn, stock, cover, summary, detail));
        }
        if (result.isEmpty() || result.size() > 200) bad("每次须导入1至200条路线");
        return result;
    }

    private static String value(List<String> row, Map<String, Integer> columns, String name) {
        int index = columns.get(name);
        return index < row.size() ? row.get(index).trim() : "";
    }

    static List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                row.add(field.toString()); field.setLength(0);
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<>();
                if (rows.size() > 201) bad("每次最多导入200条路线");
            } else field.append(c);
        }
        if (quoted) bad("CSV 引号未闭合");
        if (!row.isEmpty() || !field.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
