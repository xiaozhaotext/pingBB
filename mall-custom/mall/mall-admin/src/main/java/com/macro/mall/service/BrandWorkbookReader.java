package com.macro.mall.service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Reads CKIR multi-sheet catalogs without evaluating workbook formulas. */
public final class BrandWorkbookReader {
 private static final List<String> HEADERS=List.of("商品分类","商品名称","副标题","商品货号","售价","库存","封面图URL","商品简介","商品详情");
 private static org.w3c.dom.Document xml(java.io.InputStream in)throws Exception {
  var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);return f.newDocumentBuilder().parse(in);
 }
 private static String save(byte[] bytes,String ext,Path dir)throws Exception {
  if(bytes.length>20*1024*1024)throw new IllegalArgumentException("单张图片超过20MB");
  ext=ext.toLowerCase(Locale.ROOT);if(!Set.of("png","jpg","jpeg","gif","webp").contains(ext))throw new IllegalArgumentException("不支持的商品图片格式");
  String n=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"."+ext;
  Files.createDirectories(dir);Path dest=dir.resolve(n);if(!Files.exists(dest))Files.write(dest,bytes);
  return "/product-images/"+n;
 }
 public static List<List<String>> read(Workbook wb,Path source,Path imageDir)throws Exception {
  Map<String,String> cellImages=new HashMap<>();
  try(var zip=new ZipFile(source.toFile())) {
   var rel=zip.getEntry("xl/_rels/cellimages.xml.rels");var cells=zip.getEntry("xl/cellimages.xml");
   if(rel!=null && cells!=null){
    Map<String,String> paths=new HashMap<>();
    try(var in=zip.getInputStream(rel)){var nodes=xml(in).getElementsByTagNameNS("*","Relationship");for(int i=0;i<nodes.getLength();i++){Element e=(Element)nodes.item(i);if(!"External".equals(e.getAttribute("TargetMode")))paths.put(e.getAttribute("Id"),e.getAttribute("Target"));}}
    try(var in=zip.getInputStream(cells)){
     var pics=xml(in).getElementsByTagNameNS("*","pic");
     for(int i=0;i<pics.getLength();i++){
      Element pic=(Element)pics.item(i);var names=pic.getElementsByTagNameNS("*","cNvPr");var blips=pic.getElementsByTagNameNS("*","blip");if(names.getLength()==0||blips.getLength()==0)continue;
      String id=((Element)names.item(0)).getAttribute("name");String rid=((Element)blips.item(0)).getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","embed");String target=paths.get(rid);if(target==null)continue;
      String entry=Path.of("xl").resolve(target).normalize().toString().replace('\\','/');if(target.startsWith("/"))entry=target.substring(1);
      if(!entry.startsWith("xl/media/"))continue;var z=zip.getEntry(entry);if(z==null||z.getSize()>20*1024*1024)continue;
      try(var image=zip.getInputStream(z)){cellImages.put(id,save(image.readNBytes(20*1024*1024+1),entry.substring(entry.lastIndexOf('.')+1),imageDir));}
     }
    }
   }
  }
  var result=new ArrayList<List<String>>();result.add(HEADERS);var formatter=new DataFormatter(Locale.CHINA);
  for(Sheet sheet:wb){
   Row header=sheet.getRow(sheet.getFirstRowNum());if(header==null)continue;Map<String,Integer> cols=new HashMap<>();for(Cell c:header)cols.put(formatter.formatCellValue(c).replaceAll("\\s+", ""),c.getColumnIndex());
   if(!cols.containsKey("产品名称")||!cols.containsKey("零售价"))continue;
   Map<Integer,String> floating=new HashMap<>();
   if(sheet instanceof XSSFSheet xs && xs.getDrawingPatriarch()!=null)for(var shape:xs.getDrawingPatriarch().getShapes())if(shape instanceof XSSFPicture pic)floating.put(pic.getClientAnchor().getRow1(),save(pic.getPictureData().getData(),pic.getPictureData().suggestFileExtension(),imageDir));
   for(int line=header.getRowNum()+1;line<=sheet.getLastRowNum();line++){
    Row row=sheet.getRow(line);if(row==null)continue;Map<String,String> v=new HashMap<>();
    for(var col:cols.entrySet()){Cell c=row.getCell(col.getValue());if(c!=null){if(c.getCellType()==CellType.FORMULA){if(c.getCellFormula().contains("DISPIMG"))v.put(col.getKey(),c.getCellFormula());else v.put(col.getKey(),formatter.formatCellValue(c));}else v.put(col.getKey(),formatter.formatCellValue(c).trim());}}
    String name=v.getOrDefault("产品名称","").trim();if(name.isBlank())continue;
    String price=v.getOrDefault("零售价","").replace(",","").trim();if(price.isBlank())throw new IllegalArgumentException(sheet.getSheetName()+" 第"+(line+1)+"行缺少零售价");
    String category=v.getOrDefault("三级类目",sheet.getSheetName()).trim();if(category.isBlank())category=sheet.getSheetName();
    String detail=v.getOrDefault("信息整合",v.getOrDefault("产品信息",v.getOrDefault("产品特点",name)));if(detail.isBlank())detail=name;
    String subtitle=v.getOrDefault("品牌",name).replace("\n"," ");if(subtitle.isBlank())subtitle=name;
    String summary=detail.substring(0,Math.min(500,detail.length()));
    String image=floating.getOrDefault(line,"");String formula=v.getOrDefault("产品图片",v.getOrDefault("图片",""));
    for(var e:cellImages.entrySet())if(formula.contains(e.getKey())){image=e.getValue();break;}
    if(image.isBlank() && formula.startsWith("https://"))image=formula;
    if(image.isBlank())throw new IllegalArgumentException(sheet.getSheetName()+" 第"+(line+1)+"行未找到内嵌商品图片");
    String model=v.getOrDefault("产品型号","").trim();String suffix=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((sheet.getSheetName()+":"+line+":"+model).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,16);
    String sn="CKIR-"+suffix;
    result.add(List.of(category,name,subtitle,sn,price,v.getOrDefault("库存","100").isBlank()?"100":v.getOrDefault("库存","100"),image,summary,detail));
    if(result.size()>2001)throw new IllegalArgumentException("每次最多导入2000个商品");
   }
  }
  if(result.size()==1)throw new IllegalArgumentException("未找到包含产品名称和零售价的工作表");return result;
 }
}
