package com.macro.mall.service;

import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.model.PmsSkuStock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Explicit two-step import for the CKIR supplier workbook. All products start unpublished with zero stock. */
@Service
public class CkirProductImportService {
    private final CkirWorkbookParser parser;
    private final JdbcTemplate jdbc;
    private final PmsProductService products;
    @Value("${mall.product.image-dir:/opt/mall-demo/product-images}")
    private String imageDir;

    public CkirProductImportService(CkirWorkbookParser parser, JdbcTemplate jdbc, PmsProductService products) {
        this.parser = parser;
        this.jdbc = jdbc;
        this.products = products;
    }

    public record Preview(int products, int perZone, List<String> categories, List<String> warnings,
                          int alreadyImported) {}
    public record Result(int createdProducts, int skippedExisting, List<String> categories) {}

    public Preview preview(MultipartFile file) throws IOException {
        try (Prepared prepared = prepare(file)) {
            return describe(prepared.parsed());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result importFile(MultipartFile file) throws IOException {
        try (Prepared prepared = prepare(file); ZipFile zip = new ZipFile(prepared.path().toFile())) {
            CkirWorkbookParser.Preview parsed = prepared.parsed();
            if (!parsed.warnings().isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "表格存在" + parsed.warnings().size() + "个问题，请先查看预览");
            Map<String, Long> categories = new HashMap<>();
            Set<String> sheetNames = new LinkedHashSet<>(
                    parsed.items().stream().map(CkirWorkbookParser.Item::sheet).toList());
            for (String sheet : sheetNames)
                for (String zone : List.of("REBUY", "CONSUME"))
                    categories.put(sheet + "|" + zone, category(sheet, zone));
            int created = 0;
            int skipped = 0;
            for (CkirWorkbookParser.Item item : parsed.items()) {
                String cover = image(zip, item.imageEntry());
                for (String zone : List.of("REBUY", "CONSUME")) {
                    String sn = sn(item, zone);
                    Integer existing = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pms_product WHERE product_sn=?", Integer.class, sn);
                    if (existing != null && existing > 0) { skipped++; continue; }
                    var price = "REBUY".equals(zone) ? item.rebuyPrice() : item.consumePrice();
                    PmsProductParam product = new PmsProductParam();
                    product.setZoneCode(zone);
                    product.setProductCategoryId(categories.get(item.sheet() + "|" + zone));
                    product.setProductCategoryName(item.sheet());
                    product.setName(item.name().length() > 140 ? item.name().substring(0, 140) : item.name());
                    product.setSubTitle(item.model().length() > 255 ? item.model().substring(0, 255) : item.model());
                    product.setProductSn(sn);
                    product.setStock(0);
                    product.setLowStock(0);
                    product.setPrice(price);
                    product.setOriginalPrice(price);
                    product.setPic(cover);
                    String description = item.description().isBlank() ? item.name() : item.description();
                    product.setDescription(description.length() > 500 ? description.substring(0, 500) : description);
                    String detail = "<p>" + HtmlUtils.htmlEscape(description.length() > 10000
                            ? description.substring(0, 10000) : description).replace("\n", "<br>") + "</p>";
                    product.setDetailHtml(detail);
                    product.setDetailMobileHtml(detail);
                    product.setUnit("件");
                    product.setPublishStatus(0);
                    product.setDeleteStatus(0);
                    product.setVerifyStatus(1);
                    product.setPromotionType(0);
                    product.setGiftPoint(0);
                    product.setGiftGrowth(0);
                    product.setUsePointLimit(0);
                    PmsSkuStock sku = new PmsSkuStock();
                    sku.setPrice(price);
                    sku.setStock(0);
                    sku.setLowStock(0);
                    sku.setLockStock(0);
                    sku.setSpData("[]");
                    product.setSkuStockList(List.of(sku));
                    products.create(product);
                    created++;
                }
            }
            return new Result(created, skipped, new ArrayList<>(sheetNames));
        }
    }

    private Preview describe(CkirWorkbookParser.Preview parsed) {
        List<String> categories = new ArrayList<>(
                new LinkedHashSet<>(parsed.items().stream().map(CkirWorkbookParser.Item::sheet).toList()));
        int existing = 0;
        for (CkirWorkbookParser.Item item : parsed.items()) {
            for (String zone : List.of("REBUY", "CONSUME")) {
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pms_product WHERE product_sn=?", Integer.class, sn(item, zone));
                if (count != null && count > 0) existing++;
            }
        }
        return new Preview(parsed.items().size() * 2, parsed.items().size(), categories,
                parsed.warnings(), existing);
    }

    private static String sn(CkirWorkbookParser.Item item, String zone) {
        return "CKIR-" + zone + "-" + Integer.toUnsignedString(item.sheet().hashCode(), 36).toUpperCase()
                + "-" + item.row();
    }

    private long category(String name, String zone) {
        List<Long> existing = jdbc.queryForList(
                "SELECT c.id FROM pms_product_category c JOIN pms_category_zone z ON z.category_id=c.id "
                        + "WHERE c.parent_id=0 AND c.name=? AND z.zone_code=? ORDER BY c.id LIMIT 2",
                Long.class, name, zone);
        if (existing.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "分类重名：" + name);
        long id;
        if (existing.isEmpty()) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO pms_product_category(parent_id,name,level,product_count,product_unit,nav_status,show_status,sort,keywords,description) "
                                + "VALUES(0,?,0,0,'件',0,1,0,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name);
                statement.setString(2, name);
                statement.setString(3, name);
                return statement;
            }, key);
            id = key.getKey().longValue();
        } else id = existing.get(0);
        if (existing.isEmpty())
            jdbc.update("INSERT INTO pms_category_zone(category_id,zone_code) VALUES(?,?)", id, zone);
        return id;
    }

    private String image(ZipFile zip, String entryName) throws IOException {
        if (entryName == null || !entryName.startsWith("xl/media/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品图片无法识别");
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.getSize() > 10 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品图片缺失或超过10MB");
        byte[] bytes;
        try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(10 * 1024 * 1024 + 1); }
        if (bytes.length > 10 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品图片超过10MB");
        String ext = entryName.toLowerCase().endsWith(".png") ? ".png"
                : entryName.toLowerCase().endsWith(".webp") ? ".webp" : ".jpg";
        String hash;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            hash = java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) { throw new IOException(error); }
        Path directory = Path.of(imageDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String filename = "ckir-" + hash + ext;
        Path target = directory.resolve(filename);
        if (!Files.exists(target)) Files.write(target, bytes);
        return "/product-images/" + filename;
    }

    private Prepared prepare(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 300L * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件不能为空或超过300MB");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CKIR 导入仅支持 .xlsx");
        Path path = Files.createTempFile("ckir-import-", ".xlsx");
        try {
            try (var input = file.getInputStream()) { Files.copy(input, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            return new Prepared(path, parser.parse(path));
        } catch (Exception error) {
            Files.deleteIfExists(path);
            throw error;
        }
    }

    private record Prepared(Path path, CkirWorkbookParser.Preview parsed) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
