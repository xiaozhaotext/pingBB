package com.macro.mall.controller;
import com.macro.mall.config.AdminIpAccessFilter;
import com.macro.mall.service.P0AdminAccess;
import com.macro.mall.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import jakarta.servlet.FilterChain;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Review probes: assert intended safe behavior; failures are findings, not acceptance PASS. */
class CrossReviewRegressionTest {
 @Test void ipPolicyReadFailureMustNotSilentlyAllowExternalCaller() throws Exception {
  var settings=mock(SystemSettingService.class);
  when(settings.ipAccessRule()).thenThrow(new DataAccessResourceFailureException("test policy storage unavailable"));
  var filter=new AdminIpAccessFilter(settings);var request=new MockHttpServletRequest("GET","/p0/system-settings");request.setRemoteAddr("203.0.113.44");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
  filter.doFilter(request,response,chain);
  assertTrue(response.getStatus()==503 || response.getStatus()==403,"Policy unavailable must fail closed; actual status="+response.getStatus());verifyNoInteractions(chain);
 }
 @Test void auditFailureMustRejectSettingWrite() {
  JdbcTemplate jdbc=new JdbcTemplate(){
   @Override public int update(String sql,Object... args){if(sql.contains("INSERT INTO sys_audit_log"))throw new DataAccessResourceFailureException("test audit unavailable");return 1;}
   @Override public <T> java.util.List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object...args){return java.util.List.of();}
  };
  var settings=new SystemSettingService(jdbc,mock(RedisConnectionFactory.class));
  assertThrows(DataAccessResourceFailureException.class,()->settings.update("watermark.text","review-test",3,"test-admin","127.0.0.1"),"Must not report success with no audit record");
 }
 @Test void veryLargeImagePageMustNotOverflowIntoServerError() {
  var access=mock(P0AdminAccess.class);var jdbc=mock(JdbcTemplate.class);var settings=mock(SystemSettingService.class);Principal p=()->"admin";
  when(access.requireAuthorized(p)).thenReturn(3L);var c=new SystemSettingController(jdbc,access,settings);
  assertDoesNotThrow(()->c.imageAssets(p,"list","",Integer.MAX_VALUE,200));
 }
}
