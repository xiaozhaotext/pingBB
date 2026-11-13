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
import java.security.Principal;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/p0/column-images")
public class ColumnImageController {
    private static final Set<String> SLOTS=Set.of("hero","pick","rebuy","consume","gift1","gift2","gift3","gift4");
    private final P0AdminAccess access;
    private final JdbcTemplate jdbc;
    @Value("${mall.column-image-dir:/opt/mall-demo/column-images}") private String directory;
    public ColumnImageController(P0AdminAccess access,JdbcTemplate jdbc){this.access=access;this.jdbc=jdbc;}

    @GetMapping
    public CommonResult<Map<String,String>> list(Principal principal){
        access.requireAuthorized(principal);
        Map<String,String> result=new LinkedHashMap<>();
        for(String slot:List.of("hero","pick","rebuy","consume","gift1","gift2","gift3","gift4"))
            result.put(slot,"/static/festive-home/"+slot+".jpg");
        return CommonResult.success(result);
    }

    @PostMapping("/{slot}")
    public CommonResult<Map<String,String>> upload(Principal principal,@PathVariable String slot,@RequestPart("file") MultipartFile file) throws IOException {
        long operator=access.requirePlatformAdmin(principal);
        if(!SLOTS.contains(slot))throw new IllegalArgumentException("栏目图片位置无效");
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
}
