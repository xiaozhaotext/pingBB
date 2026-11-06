package com.macro.mall.service;

import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads CKIR's many-sheet WPS workbook without loading its embedded pictures into the Java heap. */
final class CkirWorkbookReader {
    private static final Pattern IMAGE_ID = Pattern.compile("ID_[A-Za-z0-9]+");
    record Item(String sheet, int line, Map<String, String> values, String imageEntry) {
        String get(String key) { return values.getOrDefault(key, "").trim(); }
    }
    record Result(List<Item> items, Path temporaryFile) implements AutoCloseable {
        @Override public void close() throws IOException { if (temporaryFile != null) Files.deleteIfExists(temporaryFile); }
    }

    static Result read(MultipartFile file) throws Exception {
        Path temporary = Files.createTempFile("ckir-import-", ".xlsx");
        try {
            file.transferTo(temporary);
            List<Item> items = new ArrayList<>();
            try (ZipFile zip = new ZipFile(temporary.toFile())) {
                List<String> strings = sharedStrings(zip);
                Map<String, String> workbookRels = relationships(zip, "xl/_rels/workbook.xml.rels", "xl/");
                Map<String, String> imageRels = relationships(zip, "xl/_rels/cellimages.xml.rels", "xl/");
                Map<String, String> images = imageEntries(zip, imageRels);
                Document workbook = xml(zip, "xl/workbook.xml");
                for (Element sheet : elements(workbook.getDocumentElement(), "sheet")) {
                    String name = sheet.getAttribute("name").trim();
                    String relationship = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    String entry = workbookRels.get(relationship);
                    if (entry == null || !entry.startsWith("xl/worksheets/")) continue;
                    Document sheetXml = xml(zip, entry);
                    Map<Integer, String> headings = new HashMap<>();
                    for (Element row : elements(sheetXml.getDocumentElement(), "row")) {
                        int line = Integer.parseInt(row.getAttribute("r"));
                        Map<Integer, String> cells = new HashMap<>();
                        for (Element cell : elements(row, "c")) {
                            int column = column(cell.getAttribute("r"));
                            String value = childText(cell, "v");
                            if ("s".equals(cell.getAttribute("t"))) {
                                int index = Integer.parseInt(value);
                                value = index < strings.size() ? strings.get(index) : "";
                            } else if ("inlineStr".equals(cell.getAttribute("t"))) value = childText(cell, "t");
                            cells.put(column, value == null ? "" : value.trim());
                        }
                        if (line == 1) { headings.putAll(cells); continue; }
                        Map<String, String> values = new LinkedHashMap<>();
                        for (var cell : cells.entrySet()) {
                            String heading = headings.get(cell.getKey());
                            if (heading != null && !heading.isBlank()) values.put(heading.trim(), cell.getValue());
                        }
                        if (values.getOrDefault("产品名称", "").isBlank()) continue;
                        String picture = values.getOrDefault("产品图片", values.getOrDefault("图片", ""));
                        Matcher match = IMAGE_ID.matcher(picture);
                        items.add(new Item(name, line, values, match.find() ? images.get(match.group()) : null));
                        if (items.size() > 3000) throw new IllegalArgumentException("每次最多导入3000件商品");
                    }
                }
            }
            return new Result(items, temporary);
        } catch (Exception error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    static byte[] image(Path workbook, String entry) throws IOException {
        try (ZipFile zip = new ZipFile(workbook.toFile())) {
            ZipEntry image = zip.getEntry(entry);
            if (image == null || image.getSize() < 1 || image.getSize() > 10 * 1024 * 1024)
                throw new IOException("图片缺失或超过10MB");
            try (InputStream in = zip.getInputStream(image)) { return in.readAllBytes(); }
        }
    }

    private static List<String> sharedStrings(ZipFile zip) throws Exception {
        if (zip.getEntry("xl/sharedStrings.xml") == null) return List.of();
        Document document = xml(zip, "xl/sharedStrings.xml");
        List<String> result = new ArrayList<>();
        for (Element item : elements(document.getDocumentElement(), "si")) {
            StringBuilder value = new StringBuilder();
            for (Element text : elements(item, "t")) value.append(text.getTextContent());
            result.add(value.toString());
        }
        return result;
    }

    private static Map<String, String> relationships(ZipFile zip, String entry, String base) throws Exception {
        if (zip.getEntry(entry) == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        for (Element relation : elements(xml(zip, entry).getDocumentElement(), "Relationship")) {
            String target = relation.getAttribute("Target");
            if (!target.contains("..") && !target.startsWith("/") && !target.contains(":"))
                result.put(relation.getAttribute("Id"), base + target);
        }
        return result;
    }

    private static Map<String, String> imageEntries(ZipFile zip, Map<String, String> rels) throws Exception {
        if (zip.getEntry("xl/cellimages.xml") == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        for (Element picture : elements(xml(zip, "xl/cellimages.xml").getDocumentElement(), "pic")) {
            List<Element> names = elements(picture, "cNvPr");
            List<Element> blips = elements(picture, "blip");
            if (!names.isEmpty() && !blips.isEmpty()) {
                String id = names.get(0).getAttribute("name");
                String relation = blips.get(0).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
                String entry = rels.get(relation);
                if (entry != null && entry.startsWith("xl/media/")) result.put(id, entry);
            }
        }
        return result;
    }

    private static Document xml(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > 30 * 1024 * 1024) throw new IOException("表格结构缺失或过大：" + name);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (InputStream in = zip.getInputStream(entry)) { return factory.newDocumentBuilder().parse(in); }
    }

    private static List<Element> elements(Node parent, String name) {
        var nodes = ((Element) parent).getElementsByTagNameNS("*", name);
        List<Element> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }
    private static String childText(Element parent, String name) {
        List<Element> children = elements(parent, name);
        return children.isEmpty() ? "" : children.get(0).getTextContent();
    }
    private static int column(String cellRef) {
        int result = 0;
        for (char c : cellRef.toCharArray()) {
            if (c < 'A' || c > 'Z') break;
            result = result * 26 + c - 'A' + 1;
        }
        return result - 1;
    }
}
