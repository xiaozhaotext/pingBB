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
    private static final List<String> OTHER_HEADERS = List.of("商品分类", "商品名称", "副标题", "商品货号", "售价", "库存", "封面图URL", "商品简介", "商品详情");
    private final JdbcTemplate jdbc;
    private final PmsProductService productService;

    public PickProductImportService(JdbcTemplate jdbc, PmsProductService productService) {
        this.jdbc = jdbc;
        this.productService = productService;
    }

    public record Result(int imported, List<String> names) {}
    private record Route(int row, String name, String subtitle, String sn, int stock,
                         String cover, String summary, String itinerary, String category, BigDecimal price) {}

    @Transactional(rollbackFor = Exception.class)
    public Result importFile(MultipartFile file) throws IOException {
        return importFile("PICK", file);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result importFile(String zone, MultipartFile file) throws IOException {
        if (!List.of("PICK", "REBUY", "CONSUME").contains(zone)) bad("无效的商品专区");
        boolean pick = "PICK".equals(zone);
        List<String> headers = pick ? HEADERS : OTHER_HEADERS;
        if (file.isEmpty() || file.getSize() > 400L * 1024 * 1024)
            bad("文件不能为空且不能超过400MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> rows;
        boolean brandCatalog=false;
        if (filename.endsWith(".csv")) {
            rows = parseCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            rows = new ArrayList<>();
            java.nio.file.Path temp=java.nio.file.Files.createTempFile("catalog-",filename.endsWith(".xls")?".xls":".xlsx");
            file.transferTo(temp);
            try (var workbook = WorkbookFactory.create(temp.toFile())) {
                if (workbook.getNumberOfSheets() < 1) bad("Excel 没有工作表");
                var sheet = workbook.getSheetAt(0);
                boolean brand=!pick && !new DataFormatter().formatCellValue(sheet.getRow(0).getCell(0)).equals("商品分类");
                brandCatalog=brand;
                if(brand) rows=BrandWorkbookReader.read(workbook,temp,java.nio.file.Path.of("/opt/mall-demo/product-images"));
                else {
                if (sheet.getLastRowNum() > 200) bad("每次最多导入200行");
                var formatter = new DataFormatter(Locale.CHINA);
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < headers.size(); i++) {
                        Cell cell = row.getCell(i);
                        if (cell != null && cell.getCellType() == CellType.FORMULA) bad("第" + (row.getRowNum() + 1) + "行含公式，请先粘贴为值");
                        values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    while (rows.size() < row.getRowNum()) rows.add(List.of());
                    rows.add(values);
                }
                }
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage(),e);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件无法读取，请使用下载的模板", e);
            } finally { java.nio.file.Files.deleteIfExists(temp); }
        } else {
            bad("仅支持 .csv、.xlsx、.xls 文件");
            return null;
        }
        List<Route> routes = validate(rows, pick, headers, zone);
        List<Long> category = pick ? jdbc.queryForList("SELECT id FROM pms_product_category WHERE parent_id=0 AND name='旅游路线' ORDER BY id LIMIT 1", Long.class) : List.of();
        if (pick && category.isEmpty()) bad("缺少旅游路线商品分类，请先在后台建立分类");
        List<Long> attribute = jdbc.queryForList("SELECT id FROM pms_product_attribute_category WHERE name='旅游路线' ORDER BY id LIMIT 1", Long.class);
        List<String> names = new ArrayList<>();
        for (Route route : routes) {
            PmsProductParam product = new PmsProductParam();
            product.setZoneCode(zone);
            String categoryName = pick ? "旅游路线" : route.category();
            Long categoryId = pick ? category.get(0) : findCategory(zone, categoryName, route.row(), brandCatalog);
            product.setProductCategoryId(categoryId);
            product.setProductCategoryName(categoryName);
            if (pick && !attribute.isEmpty()) product.setProductAttributeCategoryId(attribute.get(0));
            product.setName(route.name());
            product.setSubTitle(route.subtitle());
            product.setProductSn(route.sn().isBlank() ? (pick ? "TOUR-" : "IMP-") + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT) : route.sn());
            product.setStock(route.stock());
            product.setLowStock(0);
            product.setPrice(route.price());
            product.setOriginalPrice(route.price());
            product.setPic(route.cover());
            product.setDescription(route.summary());
            String detail = "<p>" + HtmlUtils.htmlEscape(route.itinerary()).replace("\n", "<br>") + "</p>";
            product.setDetailHtml(detail);
            product.setDetailMobileHtml(detail);
            product.setUnit(pick ? "条" : "件");
            product.setPublishStatus(0);
            product.setDeleteStatus(0);
            product.setVerifyStatus(1);
            product.setPromotionType(0);
            product.setGiftPoint(0);
            product.setGiftGrowth(0);
            product.setUsePointLimit(0);
            PmsSkuStock sku = new PmsSkuStock();
            sku.setPrice(route.price());
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

    private Long findCategory(String zone, String name, int line, boolean create) {
        List<Long> ids = jdbc.queryForList("SELECT c.id FROM pms_product_category c JOIN pms_category_zone z ON z.category_id=c.id WHERE c.name=? AND c.show_status=1 AND z.zone_code=? ORDER BY c.id LIMIT 2", Long.class, name, zone);
        if(ids.isEmpty() && create) {
            var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var ps=connection.prepareStatement("INSERT INTO pms_product_category(parent_id,name,level,product_count,product_unit,nav_status,show_status,sort) VALUES(0,?,0,0,'件',0,1,0)",java.sql.Statement.RETURN_GENERATED_KEYS);ps.setString(1,name);return ps;},key);
            long id=key.getKey().longValue();jdbc.update("INSERT INTO pms_category_zone(category_id,zone_code) VALUES(?,?)",id,zone);return id;
        }
        if (ids.size() != 1) bad("第" + line + "行：商品分类不存在、未启用或名称重复，请使用唯一的已启用分类名称");
        return ids.get(0);
    }

    private List<Route> validate(List<List<String>> rows, boolean pick, List<String> headers, String zone) {
        if (rows.isEmpty()) bad("文件没有表头");
        Map<String, Integer> columns = new HashMap<>();
        List<String> first = rows.get(0);
        for (int i = 0; i < first.size(); i++) columns.put(first.get(i).replace("\uFEFF", "").trim(), i);
        for (String header : headers) if (!columns.containsKey(header)) bad("缺少列：" + header);
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
            String summary = value(row, columns, pick ? "行程简介" : "商品简介");
            String detail = value(row, columns, pick ? "行程详情" : "商品详情");
            String categoryName = pick ? "旅游路线" : value(row, columns, "商品分类");
            if (categoryName.isBlank() || categoryName.length() > 64) bad("第" + line + "行：商品分类不能为空或过长");
            BigDecimal price = PRICE;
            if (!pick) {
                try { price = new BigDecimal(value(row, columns, "售价")); }
                catch (NumberFormatException e) { bad("第" + line + "行：售价无效"); }
                if (price.scale() > 2 || price.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(new BigDecimal("999999.99")) > 0)
                    bad("第" + line + "行：售价须大于0且最多两位小数");
            }
            if (name.length() < 2 || name.length() > 140 || subtitle.isBlank() || subtitle.length() > 255)
                bad("第" + line + "行：商品名称或副标题无效");
            if (sn.length() > 64 || (!sn.isBlank() && !sns.add(sn))) bad("第" + line + "行：商品货号重复或过长");
            if (!sn.isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM pms_product p JOIN pms_product_zone z ON z.product_id=p.id WHERE p.product_sn=? AND z.zone_code=?", Integer.class, sn, zone) > 0)
                bad("第" + line + "行：商品货号已存在");
            int stock;
            try { stock = Integer.parseInt(stockText); } catch (NumberFormatException e) { bad("第" + line + "行：库存必须为正整数"); return result; }
            if (stock < 1 || stock > 100000) bad("第" + line + "行：库存范围为1至100000");
            if (cover.length() > 255 || !(cover.startsWith("https://") || cover.startsWith("/product-images/")))
                bad("第" + line + "行：封面图须为 HTTPS 或后台上传的图片地址");
            if (summary.isBlank() || summary.length() > 500 || detail.isBlank() || detail.length() > 10000)
                bad("第" + line + "行：简介或详情为空、过长");
            result.add(new Route(line, name, subtitle, sn, stock, cover, summary, detail, categoryName, price));
        }
        if (result.isEmpty() || result.size() > 2000) bad("每次须导入1至2000个商品");
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
