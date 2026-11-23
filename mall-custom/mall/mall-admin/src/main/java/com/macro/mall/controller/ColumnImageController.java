package com.macro.mall.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.service.P0AdminAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.Principal;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/p0/column-images")
public class ColumnImageController {
    private static final Set<String> SLOTS=Set.of("hero","pick","rebuy","consume","gift1","gift2","gift3","gift4");
    private static final Set<String> HOME_COPY_KEYS=Set.of(
            "brand","searchHint","cartEntry","heroTitle","heroSub","announcementLabel","announcementMore",
            "quickCategoryTitle","quickCategorySub","quickCartTitle","quickCartSub",
            "pickHeading","pickHeadingSub","rebuyHeading","rebuyHeadingSub",
            "consumeHeading","consumeHeadingSub","more","pickItemSub","pickBadge","rebuyBadge","consumeBadge","addCart");
    private static final Set<String> HOME_LINK_KEYS=Set.of("brand","topCart","quickCategory","quickCart","pick","rebuy","consume");
    private static final Set<String> HOME_LINK_PATHS=Set.of("/pages/index/index","/pages/category/category","/pages/cart/cart",
            "/pages/zone/pick","/pages/zone/rebuy","/pages/zone/consume","/pages/notice/notice");
    private final P0AdminAccess access;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${mall.column-image-dir:/opt/mall-demo/column-images}") private String directory;
    public ColumnImageController(P0AdminAccess access,JdbcTemplate jdbc,ObjectMapper mapper){this.access=access;this.jdbc=jdbc;this.mapper=mapper;}

    @GetMapping("/mini-home-copy")
    public CommonResult<Map<String,String>> homeCopy(Principal principal) throws IOException {
        access.requireAuthorized(principal);
        Path file=Path.of(directory).toAbsolutePath().normalize().resolve("home-copy.json");
        if(!Files.isRegularFile(file))return CommonResult.success(Map.of());
        Map<?,?> saved=mapper.readValue(file.toFile(),Map.class);
        Map<String,String> result=new LinkedHashMap<>();
        for(String key:HOME_COPY_KEYS){
            Object value=saved.get(key);
            if(value instanceof String text)result.put(key,text);
        }
        return CommonResult.success(result);
    }

    @PutMapping("/mini-home-copy")
    public synchronized CommonResult<Boolean> saveHomeCopy(Principal principal,@RequestBody Map<String,String> copy) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(copy==null||!HOME_COPY_KEYS.containsAll(copy.keySet()))throw new IllegalArgumentException("首页文案字段无效");
        for(Map.Entry<String,String> entry:copy.entrySet()){
            String value=entry.getValue();
            if(value==null||value.isBlank()||!value.equals(value.trim())||value.codePointCount(0,value.length())>80
                    ||value.chars().anyMatch(ch->ch<32))throw new IllegalArgumentException("首页文案须为1至80字，首尾不能有空格");
        }
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve("home-copy.json"),temporary=Files.createTempFile(dir,"home-copy-",".json");
        try{
            mapper.writeValue(temporary.toFile(),copy);
            makePublicFile(temporary);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'miniapp','HOME_COPY',?)",
                principal.getName(),operator,"fields="+copy.size());
        return CommonResult.success(true);
    }

    @GetMapping("/mini-home-layout")
    public CommonResult<Map<String,Object>> homeLayout(Principal principal) throws IOException {
        access.requireAuthorized(principal);
        Path file=Path.of(directory).toAbsolutePath().normalize().resolve("home-layout.json");
        if(!Files.isRegularFile(file))return CommonResult.success(Map.of());
        Map<?,?> saved=mapper.readValue(file.toFile(),Map.class);
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("showHero","showAnnouncement","showQuickLinks","showPick","showRebuy","showConsume"))
            if(saved.get(key) instanceof Boolean value)result.put(key,value);
        if(saved.get("zoneOrder") instanceof List<?> order)result.put("zoneOrder",order);
        return CommonResult.success(result);
    }

    @PutMapping("/mini-home-layout")
    public synchronized CommonResult<Boolean> saveHomeLayout(Principal principal,@RequestBody Map<String,Object> layout) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        Set<String> keys=Set.of("showHero","showAnnouncement","showQuickLinks","showPick","showRebuy","showConsume","zoneOrder");
        if(layout==null||!layout.keySet().equals(keys))throw new IllegalArgumentException("首页板块设置不完整");
        for(String key:keys)if(!key.equals("zoneOrder")&&!(layout.get(key) instanceof Boolean))
            throw new IllegalArgumentException("板块显示设置无效");
        Object order=layout.get("zoneOrder");
        if(!(order instanceof List<?> list)||list.size()!=3||list.stream().anyMatch(item->!(item instanceof String))
                ||!Set.copyOf(list).equals(Set.of("pick","rebuy","consume")))
            throw new IllegalArgumentException("专区顺序无效");
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve("home-layout.json"),temporary=Files.createTempFile(dir,"home-layout-",".json");
        try{
            mapper.writeValue(temporary.toFile(),layout);
            makePublicFile(temporary);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'miniapp','HOME_LAYOUT',?)",
                principal.getName(),operator,"updated");
        return CommonResult.success(true);
    }

    @GetMapping("/mini-home-links")
    public CommonResult<Map<String,String>> homeLinks(Principal principal) throws IOException {
        access.requireAuthorized(principal);
        Path file=Path.of(directory).toAbsolutePath().normalize().resolve("home-links.json");
        if(!Files.isRegularFile(file))return CommonResult.success(Map.of());
        Map<?,?> saved=mapper.readValue(file.toFile(),Map.class);
        Map<String,String> result=new LinkedHashMap<>();
        for(String key:HOME_LINK_KEYS)if(saved.get(key) instanceof String value&&HOME_LINK_PATHS.contains(value))result.put(key,value);
        return CommonResult.success(result);
    }

    @PutMapping("/mini-home-links")
    public synchronized CommonResult<Boolean> saveHomeLinks(Principal principal,@RequestBody Map<String,String> links) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(links==null||!links.keySet().equals(HOME_LINK_KEYS)
                ||links.values().stream().anyMatch(value->value==null||!HOME_LINK_PATHS.contains(value)))
            throw new IllegalArgumentException("首页跳转设置无效");
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve("home-links.json"),temporary=Files.createTempFile(dir,"home-links-",".json");
        try{
            mapper.writeValue(temporary.toFile(),links);
            makePublicFile(temporary);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'miniapp','HOME_LINKS',?)",
                principal.getName(),operator,"updated");
        return CommonResult.success(true);
    }

    @PutMapping("/tourism-labels/{index}")
    public synchronized CommonResult<Boolean> saveTourismLabels(Principal principal, @PathVariable("index") int index,
                                                                  @RequestBody Map<String,String> body) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(index<1 || index>4)throw new IllegalArgumentException("旅游正品序号无效");
        String name=body == null ? null : body.get("name");
        if(name==null || name.isBlank() || !name.equals(name.trim()) || name.codePointCount(0,name.length())>12)
            throw new IllegalArgumentException("名称请输入1至12个字，首尾不能有空格");
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve("tourism-label.json"), temporary=Files.createTempFile(dir,"tourism-label-", ".json");
        try {
            Map<?,?> previous=Files.isRegularFile(target)?mapper.readValue(target.toFile(),Map.class):Map.of();
            String label=previous.get("label") instanceof String value && !value.isBlank()?value:"旅游正品";
            List<String> names=new java.util.ArrayList<>();
            Object previousNames=previous.get("names");
            for(int i=0;i<4;i++){
                Object saved=previousNames instanceof List<?> list && list.size()==4?list.get(i):null;
                names.add(saved instanceof String text && !text.isBlank()?text:label+(i+1));
            }
            names.set(index-1,name);
            mapper.writeValue(temporary.toFile(),Map.of("label",label,"names",names));
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("rw-r--r--"));
        } finally {Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','TOURISM_LABELS',?)",principal.getName(),operator,index+":"+name);
        return CommonResult.success(true);
    }

    @PutMapping("/tourism-label")
    public CommonResult<Boolean> saveTourismLabel(Principal principal, @RequestBody Map<String,String> body) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        String label=body == null ? null : body.get("label");
        if(label == null || label.isBlank() || label.codePointCount(0,label.length()) > 12 || !label.equals(label.trim()))
            throw new IllegalArgumentException("名称请输入1至12个字，首尾不能有空格");
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve("tourism-label.json"), temporary=Files.createTempFile(dir,"tourism-label-", ".json");
        try {
            Map<?,?> previous=Files.isRegularFile(target)?mapper.readValue(target.toFile(),Map.class):Map.of();
            Object names=previous.get("names");
            mapper.writeValue(temporary.toFile(),names instanceof List<?> list && list.size()==4
                    ?Map.of("label",label,"names",list):Map.of("label",label));
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("rw-r--r--"));
        } finally {Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','TOURISM_LABEL',?)",principal.getName(),operator,label);
        return CommonResult.success(true);
    }

    @PutMapping("/captions/{slot}")
    public CommonResult<Boolean> saveCaptions(Principal principal,@PathVariable("slot") String slot,@RequestBody List<Map<String,Object>> captions) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(!Set.of("hero","pick","rebuy","consume").contains(slot))throw new IllegalArgumentException("栏目位置无效");
        int expected=slot.equals("pick")?3:2;
        if(captions==null||captions.size()!=expected)throw new IllegalArgumentException("文字条目数量无效");
        for(Map<String,Object> row:captions){
            if(!(row.get("text") instanceof String text)||text.length()>100)throw new IllegalArgumentException("文字长度无效");
            if(!(row.get("visible") instanceof Boolean)||!(row.get("bold") instanceof Boolean))throw new IllegalArgumentException("显示设置无效");
            if(!(row.get("color") instanceof String color)||!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("颜色无效");
            for(String key:List.of("size","x","y")){
                if(!(row.get(key) instanceof Number number))throw new IllegalArgumentException("位置或字号无效");
                double value=number.doubleValue();
                if(!Double.isFinite(value)||value<(key.equals("size")?10:0)||value>(key.equals("size")?72:90))throw new IllegalArgumentException("位置或字号超出范围");
            }
            if(row.containsKey("font") && !Set.of("song","hei","kai","classic").contains(row.get("font")))throw new IllegalArgumentException("字体无效");
            if(row.containsKey("effect") && !Set.of("shadow","glow","outline","none").contains(row.get("effect")))throw new IllegalArgumentException("文字效果无效");
            if(row.containsKey("spacing")){
                if(!(row.get("spacing") instanceof Number spacing)||!Double.isFinite(spacing.doubleValue())||spacing.doubleValue()<0||spacing.doubleValue()>12)throw new IllegalArgumentException("字距无效");
            }
        }
        Path dir=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(dir);
        Path target=dir.resolve(slot+"-captions.json"), temporary=Files.createTempFile(dir,slot+"-captions-", ".json");
        try{
            mapper.writeValue(temporary.toFile(),captions);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            // Nginx serves these public caption settings from the image directory.
            if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("rw-r--r--"));
        }finally{Files.deleteIfExists(temporary);}
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','CAPTIONS',?)",principal.getName(),operator,slot);
        return CommonResult.success(true);
    }

    @GetMapping
    public CommonResult<Map<String,String>> list(Principal principal){
        access.requireAuthorized(principal);
        Map<String,String> result=new LinkedHashMap<>();
        for(String slot:List.of("hero","pick","rebuy","consume","gift1","gift2","gift3","gift4"))
            result.put(slot,"/static/festive-home/"+slot+".jpg");
        return CommonResult.success(result);
    }

    @PostMapping("/{slot}")
    public CommonResult<Map<String,String>> upload(Principal principal,@PathVariable("slot") String slot,@RequestPart("file") MultipartFile file) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(!SLOTS.contains(slot))throw new IllegalArgumentException("栏目图片位置无效");
        BufferedImage rgb = readColumnImage(file);
        Path dir=Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path temporary=Files.createTempFile(dir,slot+"-", ".jpg");
        try {
            if(!ImageIO.write(rgb,"jpg",temporary.toFile()))throw new IOException("图片转换失败");
            Files.move(temporary,dir.resolve(slot+".jpg"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','REPLACE',?)",principal.getName(),operator,slot);
        return CommonResult.success(Map.of("url","/static/festive-home/"+slot+".jpg?v="+System.currentTimeMillis()));
    }

    private BufferedImage readColumnImage(MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>100L*1024*1024)throw new IllegalArgumentException("请选择不超过100MB的图片");
        BufferedImage source;
        try (ImageInputStream input=ImageIO.createImageInputStream(file.getInputStream())) {
            if(input==null)throw new IllegalArgumentException("无法读取图片");
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IllegalArgumentException("图片格式无效");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input);
                int width=reader.getWidth(0), height=reader.getHeight(0);
                if(width<1 || height<1 || (long)width*height>30_000_000L)
                    throw new IllegalArgumentException("图片尺寸无效，不得超过3000万像素");
                source=reader.read(0);
            } finally { reader.dispose(); }
        }
        if(source==null || source.getWidth()<1 || source.getHeight()<1 || (long)source.getWidth()*source.getHeight()>30_000_000L)
            throw new IllegalArgumentException("图片格式或尺寸无效，不得超过3000万像素");
        BufferedImage rgb=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=rgb.createGraphics();
        graphics.setColor(Color.WHITE);graphics.fillRect(0,0,rgb.getWidth(),rgb.getHeight());
        graphics.drawImage(source,0,0,null);graphics.dispose();
        return rgb;
    }

    private static void makePublicFile(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @PostMapping("/hero-carousel")
    public synchronized CommonResult<List<String>> appendHeroCarousel(Principal principal,
            @RequestPart("files") List<MultipartFile> files) throws IOException {
        long operator = access.requirePlatformAdmin(principal);
        if (files.isEmpty() || files.size() > 8 || files.stream().mapToLong(MultipartFile::getSize).sum() > 8L * 1024 * 1024) {
            throw new IllegalArgumentException("请选择1至8张图片，处理后总大小不得超过8MB");
        }
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String revision = java.util.UUID.randomUUID().toString();
        List<String> urls = readHeroCarousel(dir);
        if (urls.size() + files.size() > 8) throw new IllegalArgumentException("首页宣传图最多保留8张");
        List<Path> written = new java.util.ArrayList<>();
        Path manifest = Files.createTempFile(dir, "hero-carousel-", ".tmp");
        boolean published = false;
        try {
            for (MultipartFile file : files) {
                String name = "hero-" + revision + "-" + urls.size() + ".jpg";
                Path target = dir.resolve(name);
                written.add(target);
                if (!ImageIO.write(readColumnImage(file), "jpg", target.toFile())) {
                    throw new IOException("图片转换失败");
                }
                makePublicFile(target);
                urls.add("/static/festive-home/" + name);
            }
            mapper.writeValue(manifest.toFile(), urls);
            makePublicFile(manifest);
            jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','REPLACE',?)",
                    principal.getName(), operator, "hero-carousel:add:" + files.size());
            Files.move(manifest, dir.resolve("hero-carousel.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
            // 保留近期替换的图片，供已加载轮播列表的客户端继续访问。
            try (var images = Files.newDirectoryStream(dir, "hero-*-*.jpg")) {
                long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
                for (Path image : images) {
                    if (!urls.contains("/static/festive-home/" + image.getFileName()) && Files.getLastModifiedTime(image).toMillis() < cutoff) {
                        Files.deleteIfExists(image);
                    }
                }
            } catch (IOException ignored) {
                // 下次上传时可以再次清理过期图片。
            }
            return CommonResult.success(urls);
        } finally {
            Files.deleteIfExists(manifest);
            if (!published) for (Path image : written) Files.deleteIfExists(image);
        }
    }

    private List<String> readHeroCarousel(Path dir) throws IOException {
        Path file = dir.resolve("hero-carousel.json");
        if (!Files.exists(file)) return new java.util.ArrayList<>(List.of("/static/festive-home/hero.jpg"));
        return mapper.readValue(file.toFile(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
    }

    @DeleteMapping("/hero-carousel")
    public synchronized CommonResult<List<String>> removeHeroImage(Principal principal,
            @RequestParam("url") String url) throws IOException {
        long operator = access.requirePlatformAdmin(principal);
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        List<String> urls = readHeroCarousel(dir);
        if (!urls.remove(url)) throw new IllegalArgumentException("图片已移除，请刷新列表");
        Path temporary = Files.createTempFile(dir, "hero-carousel-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), urls);
            makePublicFile(temporary);
            jdbc.update("INSERT INTO sys_audit_log(operator_name,operator_id,module,action,detail) VALUES(?,?,'column_image','REMOVE',?)",
                    principal.getName(), operator, url);
            Files.move(temporary, dir.resolve("hero-carousel.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return CommonResult.success(urls);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
