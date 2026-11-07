package com.macro.mall.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads CKIR multi-sheet catalogs (《CKIR-国际轻奢时尚品牌》-品牌总表) without evaluating workbook formulas.
 * <p>表格形态：多工作表、23 列、产品图片为 C 列的浮动图片（部分表用 WPS 单元格图片 DISPIMG）。
 * 每行取一个商品，工作表名即商品分类；缺失产品图片的行跳过并写入 warnings，不再整批失败。
 */
public final class BrandWorkbookReader {
    private static final List<String> HEADERS = List.of("商品分类", "商品名称", "副标题", "商品货号", "售价", "库存", "封面图URL", "商品简介", "商品详情");
    /** 复购区按电商实售价取价：平台价 → 京东价 → 零售价。 */
    private static final List<String> REBUY_PRICE_KEYS = List.of("平台价", "京东价", "零售价");
    /** 消费区按吊牌价取价：零售价 → 平台价 → 京东价。 */
    private static final List<String> CONSUME_PRICE_KEYS = List.of("零售价", "平台价", "京东价");
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_PRODUCTS = 2000;

    private BrandWorkbookReader() {
    }

    private static Document xml(java.io.InputStream in) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(in);
    }

    public static String save(byte[] bytes, String ext, Path dir) throws Exception {
        if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("单张图片超过20MB");
        ext = ext.toLowerCase(Locale.ROOT);
        if (!Set.of("png", "jpg", "jpeg", "gif", "webp").contains(ext)) throw new IllegalArgumentException("不支持的商品图片格式");
        String n = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "." + ext;
        Files.createDirectories(dir);
        Path dest = dir.resolve(n);
        if (!Files.exists(dest)) Files.write(dest, bytes);
        return "/product-images/" + n;
    }

    /**
     * @param zone     PICK/REBUY/CONSUME，决定取哪一列作为售价
     * @param warnings 跳过行的原因，回传给后台提示运营
     */
    public static List<List<String>> read(Workbook wb, Path source, Path imageDir, String zone, List<String> warnings) throws Exception {
        Map<String, String> cellImages = readCellImages(source, imageDir);
        List<String> priceKeys = "REBUY".equals(zone) ? REBUY_PRICE_KEYS : CONSUME_PRICE_KEYS;
        List<List<String>> result = new ArrayList<>();
        result.add(HEADERS);
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        for (Sheet sheet : wb) {
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) continue;
            Map<String, Integer> cols = new HashMap<>();
            for (Cell c : header) cols.put(formatter.formatCellValue(c).replaceAll("\\s+", ""), c.getColumnIndex());
            if (!cols.containsKey("产品名称")) continue;
            String priceKey = priceKeys.stream().filter(cols::containsKey).findFirst().orElse(null);
            if (priceKey == null) {
                if (sheet.getLastRowNum() > header.getRowNum())
                    warnings.add(sheet.getSheetName() + " 缺少" + String.join("/", priceKeys) + "列，整表跳过");
                continue;
            }
            Map<Integer, String> floating = new HashMap<>();
            if (sheet instanceof XSSFSheet xs && xs.getDrawingPatriarch() != null) {
                for (var shape : xs.getDrawingPatriarch().getShapes()) {
                    if (shape instanceof XSSFPicture pic) {
                        floating.put(pic.getClientAnchor().getRow1(), save(pic.getPictureData().getData(), pic.getPictureData().suggestFileExtension(), imageDir));
                    }
                }
            }
            for (int line = header.getRowNum() + 1; line <= sheet.getLastRowNum(); line++) {
                Row row = sheet.getRow(line);
                if (row == null) continue;
                Map<String, String> v = new HashMap<>();
                for (var col : cols.entrySet()) {
                    Cell c = row.getCell(col.getValue());
                    if (c == null) continue;
                    if (c.getCellType() == CellType.FORMULA) {
                        String formula = c.getCellFormula();
                        v.put(col.getKey(), formula.contains("DISPIMG") ? formula : formatter.formatCellValue(c));
                    } else {
                        v.put(col.getKey(), formatter.formatCellValue(c).trim());
                    }
                }
                String name = v.getOrDefault("产品名称", "").trim();
                if (name.isBlank()) continue;
                if (name.length() > 140) {
                    warnings.add(sheet.getSheetName() + " 第" + (line + 1) + "行商品名称超过140字，已跳过：" + name.substring(0, 40) + "…");
                    continue;
                }
                String price = v.getOrDefault(priceKey, "").replace(",", "").replace("¥", "").replace("元", "").trim();
                try {
                    BigDecimal value = new BigDecimal(price);
                    if (value.signum() <= 0 || value.scale() > 2) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    warnings.add(sheet.getSheetName() + " 第" + (line + 1) + "行" + priceKey + "无效，已跳过：" + name + "（" + price + "）");
                    continue;
                }
                String category = v.getOrDefault("三级类目", sheet.getSheetName()).trim();
                if (category.isBlank()) category = sheet.getSheetName();
                String detail = v.getOrDefault("信息整合", v.getOrDefault("产品信息", v.getOrDefault("产品特点", name)));
                if (detail.isBlank()) detail = name;
                if (detail.length() > 10000) {
                    detail = detail.substring(0, 10000);
                    warnings.add(sheet.getSheetName() + " 第" + (line + 1) + "行商品详情超过1万字，已截断：" + name);
                }
                String subtitle = v.getOrDefault("品牌", name).replace("\n", " ").trim();
                if (subtitle.isBlank()) subtitle = name;
                if (subtitle.length() > 255) subtitle = subtitle.substring(0, 255);
                String summary = detail.substring(0, Math.min(500, detail.length()));
                String image = floating.getOrDefault(line, "");
                String formula = v.getOrDefault("产品图片", v.getOrDefault("图片", ""));
                for (var e : cellImages.entrySet()) {
                    if (formula.contains(e.getKey())) {
                        image = e.getValue();
                        break;
                    }
                }
                if (image.isBlank() && formula.startsWith("https://")) image = formula;
                if (image.isBlank()) {
                    warnings.add(sheet.getSheetName() + " 第" + (line + 1) + "行没有产品图片，已跳过：" + name);
                    continue;
                }
                String model = v.getOrDefault("产品型号", "").trim();
                String suffix = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sheet.getSheetName() + ":" + line + ":" + model).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
                String sn = "CKIR-" + suffix;
                String stock = v.getOrDefault("库存", "").trim();
                result.add(List.of(category, name, subtitle, sn, price, stock.isBlank() ? "100" : stock, image, summary, detail));
                if (result.size() > MAX_PRODUCTS + 1) throw new IllegalArgumentException("每次最多导入" + MAX_PRODUCTS + "个商品");
            }
        }
        if (result.size() == 1) {
            if (!warnings.isEmpty())
                throw new IllegalArgumentException("没有可导入的商品：" + String.join("；", warnings.subList(0, Math.min(3, warnings.size()))));
            throw new IllegalArgumentException("未找到可导入的商品：工作表需包含「产品名称」与「零售价/平台价/京东价」列，且每一行都要有产品图片");
        }
        return result;
    }

    /** WPS 单元格图片（DISPIMG）→ 已落盘的图片地址。 */
    public static Map<String, String> readCellImages(Path source, Path imageDir) throws Exception {
        Map<String, String> cellImages = new HashMap<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            var rel = zip.getEntry("xl/_rels/cellimages.xml.rels");
            var cells = zip.getEntry("xl/cellimages.xml");
            if (rel == null || cells == null) return cellImages;
            Map<String, String> paths = new HashMap<>();
            try (InputStream in = zip.getInputStream(rel)) {
                var nodes = xml(in).getElementsByTagNameNS("*", "Relationship");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element e = (Element) nodes.item(i);
                    if (!"External".equals(e.getAttribute("TargetMode"))) paths.put(e.getAttribute("Id"), e.getAttribute("Target"));
                }
            }
            try (InputStream in = zip.getInputStream(cells)) {
                var pics = xml(in).getElementsByTagNameNS("*", "pic");
                for (int i = 0; i < pics.getLength(); i++) {
                    Element pic = (Element) pics.item(i);
                    var names = pic.getElementsByTagNameNS("*", "cNvPr");
                    var blips = pic.getElementsByTagNameNS("*", "blip");
                    if (names.getLength() == 0 || blips.getLength() == 0) continue;
                    String id = ((Element) names.item(0)).getAttribute("name");
                    String rid = ((Element) blips.item(0)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
                    String target = paths.get(rid);
                    if (target == null) continue;
                    String entry = Path.of("xl").resolve(target).normalize().toString().replace('\\', '/');
                    if (target.startsWith("/")) entry = target.substring(1);
                    if (!entry.startsWith("xl/media/")) continue;
                    ZipEntry z = zip.getEntry(entry);
                    if (z == null || z.getSize() > MAX_IMAGE_BYTES) continue;
                    try (InputStream image = zip.getInputStream(z)) {
                        cellImages.put(id, save(image.readNBytes((int) MAX_IMAGE_BYTES + 1), entry.substring(entry.lastIndexOf('.') + 1), imageDir));
                    }
                }
            }
        }
        return cellImages;
    }
}
