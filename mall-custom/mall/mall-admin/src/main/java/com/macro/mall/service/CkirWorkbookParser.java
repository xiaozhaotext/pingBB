package com.macro.mall.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads CKIR's multi-sheet workbook without loading its embedded images into the JVM heap. */
@Component
public class CkirWorkbookParser {
    public record Item(String sheet, int row, String name, String model, String description,
                       BigDecimal rebuyPrice, BigDecimal consumePrice, String imageEntry) {}
    public record Preview(List<Item> items, List<String> warnings) {}

    public Preview parse(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.size() > 5000) fail("Excel 文件条目过多");
            Document workbook = xml(zip, "xl/workbook.xml");
            List<String> shared = sharedStrings(zip);
            Map<String, String> cellImages = cellImages(zip);
            List<Item> items = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Set<String> models = new HashSet<>();
            NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
            if (sheets.getLength() == 0 || sheets.getLength() > 30) fail("工作表数量无效");
            for (int sheetIndex = 1; sheetIndex <= sheets.getLength(); sheetIndex++) {
                String sheet = ((Element) sheets.item(sheetIndex - 1)).getAttribute("name").trim();
                if (sheet.isBlank() || sheet.length() > 64) fail("工作表名称无效");
                String path = "xl/worksheets/sheet" + sheetIndex + ".xml";
                Document document = xml(zip, path);
                // This supplier workbook keeps sheet 1 images in WPS cellimages; drawings start at sheet 2.
                Map<Integer, String> drawingImages = sheetIndex == 1 ? Map.of() : drawingImages(zip, sheetIndex - 1);
                NodeList rows = document.getElementsByTagNameNS("*", "row");
                if (rows.getLength() < 2) continue;
                Map<String, String> headers = cells((Element) rows.item(0), shared);
                Map<String, String> names = new HashMap<>();
                for (var entry : headers.entrySet()) names.put(normalize(entry.getValue()), entry.getKey());
                String nameColumn = names.get("产品名称");
                String modelColumn = names.get("产品型号");
                String retailColumn = names.get("零售价");
                String platformColumn = names.get("平台价");
                String jdColumn = names.get("京东价");
                String rebuyColumn = platformColumn == null ? jdColumn : platformColumn;
                String imageColumn = names.getOrDefault("产品图片", names.get("图片"));
                String detailColumn = names.getOrDefault("产品信息", names.getOrDefault("产品特点", names.get("信息整合")));
                if (nameColumn == null || modelColumn == null || retailColumn == null || rebuyColumn == null)
                    fail(sheet + " 缺少产品名称、产品型号、零售价或复购区价格列");
                for (int i = 1; i < rows.getLength(); i++) {
                    Element row = (Element) rows.item(i);
                    int line = Integer.parseInt(row.getAttribute("r"));
                    Map<String, String> data = cells(row, shared);
                    String name = data.getOrDefault(nameColumn, "").trim();
                    if (name.isBlank()) continue;
                    String model = data.getOrDefault(modelColumn, "").trim();
                    if (model.isBlank()) fail(sheet + " 第" + line + "行缺少产品型号");
                    if (!models.add(sheet + "|" + model))
                        warnings.add(sheet + " 第" + line + "行型号重复，导入时需按行号区分：" + model);
                    String rebuyText = data.getOrDefault(rebuyColumn, "");
                    if (rebuyText.isBlank() && jdColumn != null) rebuyText = data.getOrDefault(jdColumn, "");
                    BigDecimal retail;
                    BigDecimal rebuy;
                    try {
                        retail = price(data.getOrDefault(retailColumn, ""), sheet, line, "零售价");
                        rebuy = price(rebuyText, sheet, line, "复购区售价");
                    } catch (ResponseStatusException error) {
                        warnings.add(error.getReason());
                        continue;
                    }
                    String image = imageColumn == null ? null : data.get(imageColumn);
                    String imageEntry = image == null ? null : cellImages.get(image.replaceAll(".*(ID_[A-Za-z0-9]+).*", "$1"));
                    if (imageEntry == null) imageEntry = drawingImages.get(line);
                    if (imageEntry == null) warnings.add(sheet + " 第" + line + "行没有可识别的图片");
                    String description = detailColumn == null ? "" : data.getOrDefault(detailColumn, "");
                    items.add(new Item(sheet, line, name, model, description, rebuy, retail, imageEntry));
                }
            }
            if (items.isEmpty()) fail("没有可导入的商品");
            return new Preview(items, warnings);
        }
    }

    private static BigDecimal price(String value, String sheet, int row, String label) {
        try {
            BigDecimal result = new BigDecimal(value.replace(",", "").replace("¥", "").trim());
            if (result.signum() <= 0 || result.scale() > 2 || result.compareTo(new BigDecimal("999999.99")) > 0)
                throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) {
            fail(sheet + " 第" + row + "行" + label + "无效：" + value);
            return BigDecimal.ZERO;
        }
    }

    private static String normalize(String value) { return value.replaceAll("\\s+", "").trim(); }
    private static void fail(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private static Document xml(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.getSize() > 20 * 1024 * 1024) fail("Excel 缺少或含过大的 " + path);
        try (InputStream stream = zip.getInputStream(entry)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(stream));
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("无法读取 Excel：" + path, error);
        }
    }

    private static List<String> sharedStrings(ZipFile zip) throws IOException {
        if (zip.getEntry("xl/sharedStrings.xml") == null) return List.of();
        Document document = xml(zip, "xl/sharedStrings.xml");
        NodeList nodes = document.getElementsByTagNameNS("*", "si");
        List<String> values = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            NodeList texts = ((Element) nodes.item(i)).getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) value.append(texts.item(j).getTextContent());
            values.add(value.toString());
        }
        return values;
    }

    private static Map<String, String> cells(Element row, List<String> shared) {
        Map<String, String> result = new HashMap<>();
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            String column = cell.getAttribute("r").replaceAll("[0-9]", "");
            String value = first(cell, "v");
            if ("s".equals(cell.getAttribute("t")) && !value.isBlank()) value = shared.get(Integer.parseInt(value));
            if ("inlineStr".equals(cell.getAttribute("t"))) value = all(cell, "t");
            String formula = first(cell, "f");
            if (formula.startsWith("DISPIMG")) value = formula;
            result.put(column, value);
        }
        return result;
    }

    private static String first(Element element, String name) {
        NodeList nodes = element.getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }
    private static String all(Element element, String name) {
        NodeList nodes = element.getElementsByTagNameNS("*", name);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) value.append(nodes.item(i).getTextContent());
        return value.toString();
    }

    private static Map<String, String> relationships(ZipFile zip, String path, String base) throws IOException {
        if (zip.getEntry(path) == null) return Map.of();
        Document document = xml(zip, path);
        Map<String, String> result = new HashMap<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            String target = item.getAttribute("Target");
            if (target.startsWith("http:") || target.startsWith("https:")) continue;
            String resolved = Path.of(base).resolve(target).normalize().toString().replace('\\', '/');
            if (resolved.startsWith("xl/")) result.put(item.getAttribute("Id"), resolved);
        }
        return result;
    }

    private static Map<String, String> cellImages(ZipFile zip) throws IOException {
        if (zip.getEntry("xl/cellimages.xml") == null) return Map.of();
        Map<String, String> rel = relationships(zip, "xl/_rels/cellimages.xml.rels", "xl");
        Document document = xml(zip, "xl/cellimages.xml");
        Map<String, String> result = new HashMap<>();
        NodeList pictures = document.getElementsByTagNameNS("*", "pic");
        for (int i = 0; i < pictures.getLength(); i++) {
            Element picture = (Element) pictures.item(i);
            NodeList names = picture.getElementsByTagNameNS("*", "cNvPr");
            NodeList blips = picture.getElementsByTagNameNS("*", "blip");
            if (names.getLength() == 0 || blips.getLength() == 0) continue;
            String name = ((Element) names.item(0)).getAttribute("name");
            String id = ((Element) blips.item(0)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
            if (rel.containsKey(id)) result.put(name, rel.get(id));
        }
        return result;
    }

    private static Map<Integer, String> drawingImages(ZipFile zip, int sheet) throws IOException {
        String path = "xl/drawings/drawing" + sheet + ".xml";
        if (zip.getEntry(path) == null) return Map.of();
        Map<String, String> rel = relationships(zip, "xl/drawings/_rels/drawing" + sheet + ".xml.rels", "xl/drawings");
        Document document = xml(zip, path);
        Map<Integer, String> result = new HashMap<>();
        NodeList anchors = document.getElementsByTagNameNS("*", "twoCellAnchor");
        for (int i = 0; i < anchors.getLength(); i++) {
            Element anchor = (Element) anchors.item(i);
            NodeList from = anchor.getElementsByTagNameNS("*", "from");
            NodeList blips = anchor.getElementsByTagNameNS("*", "blip");
            if (from.getLength() == 0 || blips.getLength() == 0) continue;
            String row = first((Element) from.item(0), "row");
            String id = ((Element) blips.item(0)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
            if (!row.isBlank() && rel.containsKey(id)) result.putIfAbsent(Integer.parseInt(row) + 1, rel.get(id));
        }
        return result;
    }
}
