package com.macro.mall.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Map;

@Service
public class SiteProfileService {
    private final JdbcTemplate jdbc;
    public SiteProfileService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,Object> read() {
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM sys_site_profile WHERE id=1");
        String[][] fields={{"company.name","company_name"},{"company.address","address"},{"company.phone","telephone"},{"company.mobile","mobile"},{"company.email","email"},{"company.qq","qq"},{"company.wechat_qrcode","wechat_qr_url"}};
        for(String[] field:fields) {
            java.util.List<String> values=jdbc.query("SELECT config_value FROM sys_config WHERE config_key=?",(rs,n)->rs.getString(1),field[0]);
            row.put(field[1],values.isEmpty() || values.get(0)==null?"":values.get(0));
        }
        return row;
    }
    public static String text(Map<String,Object> body,String key,int max) {
        Object value=body.get(key);
        if (!(value instanceof String) || ((String)value).length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"字段无效："+key);
        return ((String)value).trim();
    }
    public static long revision(Map<String,Object> body) {
        Object value=body.get("revision");
        if (!(value instanceof Number) || ((Number)value).doubleValue()!=((Number)value).longValue() || ((Number)value).longValue()<0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"版本号无效");
        return ((Number)value).longValue();
    }
    private void checkRevision(String column,long expected) {
        Long actual=jdbc.queryForObject("SELECT "+column+" FROM sys_site_profile WHERE id=1 FOR UPDATE",Long.class);
        if (actual==null || actual!=expected)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"内容已被其他管理员更新，请刷新后重试");
    }
    @Transactional(rollbackFor=Exception.class)
    public void company(Map<String,Object> body,long operator,String name) {
        long version=revision(body);
        String company=text(body,"companyName",128), address=text(body,"address",500),
            tel=text(body,"telephone",64), mobile=text(body,"mobile",32), email=text(body,"email",128),
            qq=text(body,"qq",32), qr=text(body,"wechatQrUrl",512);
        if (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"邮箱格式无效");
        if (!qr.isEmpty()) {
            try { URI uri=URI.create(qr); if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"二维码图片地址必须是HTTPS链接"); }
        }
        checkRevision("company_revision",version);
        jdbc.update("UPDATE sys_site_profile SET company_name=?,address=?,telephone=?,mobile=?,email=?,qq=?,wechat_qr_url=?,company_revision=company_revision+1 WHERE id=1",company,address,tel,mobile,email,qq,qr);
        String[][] values={{"company.name",company},{"company.address",address},{"company.phone",tel},{"company.mobile",mobile},{"company.email",email},{"company.qq",qq},{"company.wechat_qrcode",qr}};
        for(String[] value:values) jdbc.update("INSERT INTO sys_config(config_key,config_value,config_group,remark) VALUES(?,?,'company','公司信息') ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)",value[0],value[1]);
        audit(operator,name,"company_update","版本 "+version+" -> "+(version+1));
    }
    @Transactional(rollbackFor=Exception.class)
    public void draft(Map<String,Object> body,long operator,String name) {
        long version=revision(body);String title=text(body,"title",128),content=text(body,"content",50000);
        checkRevision("privacy_revision",version);
        jdbc.update("UPDATE sys_site_profile SET privacy_title=?,privacy_content=?,privacy_revision=privacy_revision+1 WHERE id=1",title,content);
        audit(operator,name,"privacy_draft","版本 "+version+" -> "+(version+1));
    }
    @Transactional(rollbackFor=Exception.class)
    public void publish(Map<String,Object> body,long operator,String name) {
        long version=revision(body);
        if (!Boolean.TRUE.equals(body.get("confirmed"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请确认政策内容已审核");
        checkRevision("privacy_revision",version);Map<String,Object> row=read();
        if (row.get("privacy_title").toString().isBlank() || row.get("privacy_content").toString().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不能发布空政策");
        if (row.get("published_revision")!=null && ((Number)row.get("published_revision")).longValue()==version) return;
        jdbc.update("UPDATE sys_site_profile SET published_title=privacy_title,published_content=privacy_content,published_revision=privacy_revision,published_at=NOW() WHERE id=1");
        jdbc.update("INSERT INTO sys_content(content_key,content_value,operator) VALUES('privacy_policy',?,?) ON DUPLICATE KEY UPDATE content_value=VALUES(content_value),operator=VALUES(operator)",row.get("privacy_content"),name);
        audit(operator,name,"privacy_publish","审核发布版本 "+version);
    }
    private void audit(long operator,String name,String action,String detail) {
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail) VALUES(?,?,'site_profile',?,1,?)",operator,name,action,detail);
    }
}
