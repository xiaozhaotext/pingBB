package com.macro.mall.service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SiteProfileServiceMysqlTest {
    private JdbcTemplate jdbc;private SiteProfileService service;private TransactionTemplate tx;
    @BeforeEach void setup() {
        String url=System.getenv("MALL_MEMBERSHIP_TEST_JDBC_URL");
        assertNotNull(url,"This acceptance test requires isolated MySQL; do not count missing environment as PASS");
        assertTrue(url.contains("127.0.0.1") && url.contains("mall_github_audit_"));
        var ds=new DriverManagerDataSource(url,System.getenv("MALL_MEMBERSHIP_TEST_DB_USER"),System.getenv("MALL_MEMBERSHIP_TEST_DB_PASSWORD"));
        jdbc=new JdbcTemplate(ds);service=new SiteProfileService(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    private long revision(String column) {return ((Number)service.read().get(column)).longValue();}
    private Map<String,Object> company(long v) {return Map.of("revision",v,"companyName","测试公司","address","测试地址","telephone","021-00000000","mobile","13900000000","email","test@example.com","qq","12345","wechatQrUrl","https://example.com/qr.png");}
    @Test void companyPersistsAndStaleEditRejected() {
        tx.executeWithoutResult(s->{long v=revision("company_revision");service.company(company(v),3,"test-admin");assertEquals("测试公司",service.read().get("company_name"));assertEquals(v+1,revision("company_revision"));assertThrows(ResponseStatusException.class,()->service.company(company(v),3,"test-admin"));s.setRollbackOnly();});
    }
    @Test void draftCannotLeakIntoPublishedPolicy() {
        tx.executeWithoutResult(s->{Map<String,Object> before=service.read();long v=revision("privacy_revision");service.draft(Map.of("revision",v,"title","测试草稿","content","未发布正文"),3,"test-admin");assertEquals(before.get("published_content"),service.read().get("published_content"));assertEquals(before.get("published_revision"),service.read().get("published_revision"));s.setRollbackOnly();});
    }
    @Test void publishRequiresConfirmationAndIsIdempotent() {
        tx.executeWithoutResult(s->{long v=revision("privacy_revision");service.draft(Map.of("revision",v,"title","测试政策","content","仅供测试"),3,"test-admin");long next=v+1;assertThrows(ResponseStatusException.class,()->service.publish(Map.of("revision",next,"confirmed",false),3,"test-admin"));service.publish(Map.of("revision",next,"confirmed",true),3,"test-admin");assertEquals("仅供测试",service.read().get("published_content"));long audit=jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE module='site_profile' AND action='privacy_publish'",Long.class);service.publish(Map.of("revision",next,"confirmed",true),3,"test-admin");assertEquals(audit,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE module='site_profile' AND action='privacy_publish'",Long.class));assertThrows(ResponseStatusException.class,()->service.publish(Map.of("revision",v,"confirmed",true),3,"test-admin"));s.setRollbackOnly();});
    }
    @Test void invalidInputsAndFailureLeaveNoDatabaseWrite() {
        long before=revision("company_revision");var bad=new java.util.HashMap<>(company(before));bad.put("wechatQrUrl","javascript:alert(1)");assertThrows(ResponseStatusException.class,()->service.company(bad,3,"test-admin"));assertEquals(before,revision("company_revision"));assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{service.company(company(before),3,"test-admin");throw new IllegalStateException("rollback test");}));assertEquals(before,revision("company_revision"));
        assertThrows(ResponseStatusException.class,()->SiteProfileService.revision(Map.of("revision",1.5)));
        assertThrows(ResponseStatusException.class,()->SiteProfileService.text(Map.of("title",42),"title",128));
    }
}
