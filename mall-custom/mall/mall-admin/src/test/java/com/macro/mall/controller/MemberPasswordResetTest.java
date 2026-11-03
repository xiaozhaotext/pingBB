package com.macro.mall.controller;
import com.macro.mall.service.*;
import com.macro.mall.common.service.RedisService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MemberPasswordResetTest {
 JdbcTemplate jdbc;TransactionTemplate tx;P0AdminController controller;P0AdminAccess access;RedisService redis;
 @BeforeEach void setup(){
 var ds=new DriverManagerDataSource("jdbc:h2:mem:reset_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
 jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
 jdbc.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY,username VARCHAR(100),password VARCHAR(100))");
 jdbc.execute("CREATE TABLE sys_audit_log(operator_id BIGINT,operator_name VARCHAR(100),module VARCHAR(100),action VARCHAR(100),target_id BIGINT,detail VARCHAR(500))");
 jdbc.update("INSERT INTO ums_member VALUES(1,'alice','old-hash'),(2,'bob','untouched')");
 access=mock(P0AdminAccess.class);redis=mock(RedisService.class);controller=new P0AdminController(jdbc,access,mock(P0ReferralAdminService.class));
 ReflectionTestUtils.setField(controller,"memberRedis",redis);ReflectionTestUtils.setField(controller,"memberRedisDatabase","mall");ReflectionTestUtils.setField(controller,"memberRedisKey","ums:member");
 when(access.requireAuthorized(any())).thenReturn(99L);
 }
 @Test void resetHashesPasswordAuditsAndEvictsOnlyTargetCache(){
 tx.executeWithoutResult(s->controller.resetMemberPassword(()->"admin",1));
 assertTrue(new BCryptPasswordEncoder().matches("12345678",jdbc.queryForObject("SELECT password FROM ums_member WHERE id=1",String.class)));
 assertEquals("untouched",jdbc.queryForObject("SELECT password FROM ums_member WHERE id=2",String.class));
 assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log",Integer.class));
 verify(redis,times(2)).del("mall:ums:member:alice");
 }
 @Test void auditFailureRollsBackPassword(){
 jdbc.execute("DROP TABLE sys_audit_log");assertThrows(Exception.class,()->tx.executeWithoutResult(s->controller.resetMemberPassword(()->"admin",1)));
 assertEquals("old-hash",jdbc.queryForObject("SELECT password FROM ums_member WHERE id=1",String.class));
 }
 @Test void unauthorizedAndMissingMembersCannotReset(){
 assertThrows(Exception.class,()->tx.executeWithoutResult(s->controller.resetMemberPassword(()->"admin",500)));
 when(access.requireAuthorized(any())).thenThrow(new SecurityException());
 assertThrows(SecurityException.class,()->tx.executeWithoutResult(s->controller.resetMemberPassword(()->"other",1)));
 assertEquals("old-hash",jdbc.queryForObject("SELECT password FROM ums_member WHERE id=1",String.class));
 }
 @Test void dashboardCountsEachAccountOnceByCurrentTier(){
 jdbc.execute("CREATE TABLE tri_member(member_id BIGINT PRIMARY KEY,tier VARCHAR(20))");
 jdbc.update("INSERT INTO ums_member VALUES(3,'c','x'),(4,'d','x'),(5,'e','x')");
 jdbc.update("INSERT INTO tri_member VALUES(2,'MEMBER'),(3,'PARTNER'),(4,'COFOUNDER')");
 var counts=new DashboardController(jdbc).memberTierCounts();
 assertEquals(2,((Number)counts.get("visitor")).intValue());
 assertEquals(1,((Number)counts.get("member")).intValue());
 assertEquals(1,((Number)counts.get("partner")).intValue());
 assertEquals(1,((Number)counts.get("cofounder")).intValue());
 }
}
