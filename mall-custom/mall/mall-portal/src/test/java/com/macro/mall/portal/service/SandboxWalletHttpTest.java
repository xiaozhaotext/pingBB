package com.macro.mall.portal.service;

import com.macro.mall.common.membership.*;
import com.macro.mall.common.exception.GlobalExceptionHandler;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.model.*;
import com.macro.mall.mapper.*;
import com.macro.mall.portal.dao.*;
import com.macro.mall.portal.domain.*;
import com.macro.mall.portal.controller.*;
import com.macro.mall.portal.config.RechargeProperties;
import com.macro.mall.portal.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.mybatis.spring.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Embedded HTTP + real transaction, MyBatis earnings and production order orchestration.
 * Login, catalog/mappers and Redis are isolated test adapters; money and stock use H2 SQL. */
@SpringBootTest(classes=SandboxWalletHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"mall.payment.mock.enabled=true","mall.membership-v2.enabled=true","logging.level.root=WARN","spring.autoconfigure.exclude=com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure"})
@org.springframework.test.context.ActiveProfiles("sandbox-wallet-test")
class SandboxWalletHttpTest {
 @Configuration @org.springframework.context.annotation.Profile("sandbox-wallet-test")
 @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class,ManagementWebSecurityAutoConfiguration.class})
 @EnableTransactionManagement(proxyTargetClass=true)
 @Import({RechargeProperties.class,RechargeService.class,SandboxWalletService.class,EarningsServiceImpl.class,RechargeController.class,WithdrawController.class,EarningsController.class,TriPoolService.class,OmsPortalOrderController.class,GlobalExceptionHandler.class})
 static class App {
  @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:mem:sandboxwallet;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");}
  @Bean UmsMemberService members(){return mock(UmsMemberService.class);}
  @Bean SqlSessionTemplate sql(DataSource ds)throws Exception {
   var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);
   config.addMapper(UmsEarningsMapper.class);config.addMapper(UmsWithdrawApplyMapper.class);config.addMapper(UmsEarningsLedgerMapper.class);
   var f=new SqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(config);return new SqlSessionTemplate(f.getObject());
  }
  @Bean UmsEarningsMapper earningsMapper(SqlSessionTemplate s){return s.getMapper(UmsEarningsMapper.class);}
  @Bean UmsWithdrawApplyMapper withdrawMapper(SqlSessionTemplate s){return s.getMapper(UmsWithdrawApplyMapper.class);}
  @Bean UmsEarningsLedgerMapper ledgerMapper(SqlSessionTemplate s){return s.getMapper(UmsEarningsLedgerMapper.class);}
  @Bean OmsPortalOrderService orders(JdbcTemplate j,UmsMemberService members,RechargeService recharge,org.springframework.transaction.PlatformTransactionManager manager){
   var service=new OmsPortalOrderServiceImpl();
   var cart=mock(OmsCartItemService.class);when(cart.listPromotion(eq(1L),anyList())).thenAnswer(c->{
    var item=new CartPromotionItem();item.setId(1L);item.setProductId(1L);item.setProductSkuId(1L);item.setProductName("沙盒自选商品");item.setZoneCode("PICK");item.setPrice(j.queryForObject("SELECT price FROM qa_stock WHERE id=1",BigDecimal.class));item.setQuantity(1);item.setRealStock(10);item.setReduceAmount(BigDecimal.ZERO);item.setIntegration(0);item.setGrowth(0);return List.of(item);
   });
   var address=new UmsMemberReceiveAddress();address.setId(1L);address.setMemberId(1L);address.setName("测试");address.setPhoneNumber("13000000000");address.setDefaultStatus(1);address.setProvince("上海市");address.setCity("上海市");address.setRegion("测试区");address.setDetailAddress("沙盒演示地址");
   var addresses=mock(UmsMemberReceiveAddressService.class);when(addresses.getItem(1L)).thenReturn(address);when(addresses.list()).thenReturn(List.of(address));
   var wallet=mock(PointsWalletService.class);when(wallet.getAccount(anyLong(),anyInt())).thenAnswer(c->{var a=new UmsPointsAccount();a.setAvailable(0L);return a;});
   var orderMapper=mock(OmsOrderMapper.class);var sequence=new AtomicLong(100);
   when(orderMapper.insert(any())).thenAnswer(c->{OmsOrder o=c.getArgument(0);o.setId(sequence.incrementAndGet());return j.update("INSERT INTO qa_order(id,member_id,status,pay_amount,balance_amount) VALUES(?,?,?,?,?)",o.getId(),o.getMemberId(),o.getStatus(),o.getPayAmount(),o.getBalanceAmount());});
   when(orderMapper.selectByPrimaryKey(anyLong())).thenAnswer(c->{var rows=j.queryForList("SELECT * FROM qa_order WHERE id=?",(Object)c.getArgument(0));if(rows.isEmpty())return null;var row=rows.get(0);var o=new OmsOrder();o.setId(((Number)row.get("id")).longValue());o.setMemberId(((Number)row.get("member_id")).longValue());o.setStatus(((Number)row.get("status")).intValue());o.setPayAmount((BigDecimal)row.get("pay_amount"));o.setBalanceAmount((BigDecimal)row.get("balance_amount"));return o;});
   var dao=mock(PortalOrderDao.class);
   when(dao.reserveSku(anyLong(),anyInt())).thenAnswer(c->j.update("UPDATE qa_stock SET reserved=reserved+? WHERE id=? AND stock-reserved>=?",c.getArgument(1),c.getArgument(0),c.getArgument(1)));
   when(dao.consumeReservedSku(anyLong(),anyInt())).thenAnswer(c->j.update("UPDATE qa_stock SET reserved=reserved-?,stock=stock-? WHERE id=? AND reserved>=?",c.getArgument(1),c.getArgument(1),c.getArgument(0),c.getArgument(1)));
   when(dao.markPaidIfPending(anyLong(),anyInt())).thenAnswer(c->j.update("UPDATE qa_order SET status=1 WHERE id=? AND status=0",(Object)c.getArgument(0)));
   when(dao.getDetail(anyLong())).thenAnswer(c->{var o=orderMapper.selectByPrimaryKey(c.getArgument(0));var detail=new OmsOrderDetail();detail.setId(o.getId());detail.setMemberId(o.getMemberId());detail.setPayAmount(o.getPayAmount());var item=new OmsOrderItem();item.setProductSkuId(1L);item.setProductQuantity(1);detail.setOrderItemList(List.of(item));return detail;});
   var items=mock(PortalOrderItemDao.class);when(items.insertList(anyList())).thenAnswer(c->{List<OmsOrderItem> list=c.getArgument(0);for(var item:list)j.update("INSERT INTO qa_order_item(order_id,sku_id) VALUES(?,?)",item.getOrderId(),item.getProductSkuId());return list.size();});
   var redis=mock(RedisService.class);when(redis.incr(anyString(),anyLong())).thenAnswer(c->sequence.incrementAndGet());
   var tri=mock(TriMembershipOrderService.class);when(tri.enabled()).thenReturn(true);
   var settings=mock(OmsOrderSettingMapper.class);when(settings.selectByExample(any())).thenReturn(List.of());
   Map<String,Object> fields=new HashMap<>();fields.put("memberService",members);fields.put("cartItemService",cart);fields.put("memberReceiveAddressService",addresses);fields.put("memberCouponService",mock(UmsMemberCouponService.class));fields.put("integrationConsumeSettingMapper",mock(UmsIntegrationConsumeSettingMapper.class));fields.put("pointsWalletService",wallet);fields.put("orderMapper",orderMapper);fields.put("orderItemMapper",mock(OmsOrderItemMapper.class));fields.put("orderItemDao",items);fields.put("portalOrderDao",dao);fields.put("redisService",redis);fields.put("triMembershipOrders",tri);fields.put("orderSettingMapper",settings);fields.put("jdbcTemplate",j);fields.put("rechargeService",recharge);fields.put("zoneSettlementService",new ZoneSettlementService(j));fields.put("REDIS_DATABASE","test");fields.put("REDIS_KEY_ORDER_ID","order");
   fields.forEach((k,v)->ReflectionTestUtils.setField(service,k,v));
   var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
   return (OmsPortalOrderService)java.lang.reflect.Proxy.newProxyInstance(OmsPortalOrderService.class.getClassLoader(),new Class<?>[]{OmsPortalOrderService.class},(proxy,method,args)->tx.execute(s->{
    try{return method.invoke(service,args);}catch(java.lang.reflect.InvocationTargetException ex){if(ex.getCause() instanceof RuntimeException runtime)throw runtime;throw new IllegalStateException(ex.getCause());}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}
   }));
  }
 }
 @Autowired JdbcTemplate j;@Autowired TestRestTemplate http;@Autowired RechargeService recharge;@Autowired UmsMemberService members;@Autowired TriPoolService pools;
 @BeforeEach void setup()throws Exception {
  j.execute("DROP ALL OBJECTS");ReflectionTestUtils.setField(recharge,"sandboxEnabled",true);
  var m=new UmsMember();m.setId(1L);m.setUsername("sandbox-test");when(members.getCurrentMember()).thenReturn(m);
  j.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY,username VARCHAR(50))");j.update("INSERT INTO ums_member VALUES(1,'sandbox-test'),(2,'other-member')");
  j.execute("CREATE TABLE sys_config(config_key VARCHAR(100),config_value VARCHAR(100))");
  j.execute("CREATE TABLE ums_recharge_account(member_id BIGINT PRIMARY KEY,balance_cents BIGINT NOT NULL DEFAULT 0)");
  j.execute("CREATE TABLE ums_recharge_order(order_no VARCHAR(40) PRIMARY KEY,member_id BIGINT,request_id VARCHAR(64),amount_cents BIGINT,channel VARCHAR(10),status VARCHAR(12) DEFAULT 'PENDING',transaction_id VARCHAR(128),balance_after_cents BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,paid_at TIMESTAMP,checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(member_id,request_id),UNIQUE(channel,transaction_id))");
  j.execute("CREATE TABLE ums_recharge_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(32),amount_cents BIGINT,balance_after_cents BIGINT,biz_id VARCHAR(64),idempotency_key VARCHAR(64) UNIQUE,remark VARCHAR(255),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  j.execute("CREATE TABLE ums_earnings(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT UNIQUE,total_earnings DECIMAL(18,2),available_earnings DECIMAL(18,2),frozen_earnings DECIMAL(18,2),withdrawn DECIMAL(18,2),version BIGINT)");
  j.execute("CREATE TABLE ums_earnings_ledger(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,change_type VARCHAR(50),amount DECIMAL(18,2),balance_after DECIMAL(18,2),biz_id VARCHAR(100),idempotency_key VARCHAR(100) UNIQUE,remark VARCHAR(255),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  j.execute("CREATE TABLE ums_withdraw_apply(id BIGINT AUTO_INCREMENT PRIMARY KEY,member_id BIGINT,idempotency_key VARCHAR(64),amount DECIMAL(18,2),fee_rate DECIMAL(10,6),fee_amount DECIMAL(18,2),actual_amount DECIMAL(18,2),status INT,simulate_flag INT,audit_id BIGINT,audit_remark VARCHAR(255),audit_time TIMESTAMP,UNIQUE(member_id,idempotency_key))");
  String sql=Files.readString(Path.of(System.getProperty("basedir","."),"../document/sql/custom-v10/50_three_dividend_pools.sql"));sql=sql.substring(0,sql.indexOf("-- Menu/resource additions")).replaceAll("(?m)^--.*$","");for(String statement:sql.split(";"))if(!statement.isBlank())j.execute(statement);
  j.execute("CREATE TABLE sys_audit_log(id BIGINT AUTO_INCREMENT PRIMARY KEY,operator_name VARCHAR(100),operator_id BIGINT,module VARCHAR(50),action VARCHAR(100),target_id BIGINT,detail VARCHAR(2000))");
  j.execute("CREATE TABLE qa_stock(id BIGINT PRIMARY KEY,price DECIMAL(18,2),stock INT,reserved INT)");j.update("INSERT INTO qa_stock VALUES(1,80,10,0)");
  j.execute("CREATE TABLE qa_order(id BIGINT PRIMARY KEY,member_id BIGINT,status INT,pay_amount DECIMAL(18,2),balance_amount DECIMAL(18,2))");j.execute("CREATE TABLE qa_order_item(order_id BIGINT,sku_id BIGINT)");
  j.execute("CREATE TABLE oms_order_request(member_id BIGINT,idempotency_key VARCHAR(100),request_hash VARCHAR(100),state VARCHAR(20),order_id BIGINT,UNIQUE(member_id,idempotency_key))");
  j.execute("CREATE TABLE oms_payment_confirmation(order_id BIGINT UNIQUE,channel_code VARCHAR(32),channel_trade_no VARCHAR(100),paid_amount DECIMAL(18,2),currency_code VARCHAR(3))");
 }
 void fundAndSettle(){
  pools.acceptPayment("MEMBERSHIP:1",1L,null,TriPoolRules.PRICE,(m,t,p,k)->{});
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM tri_pool_distribution",Integer.class));
  var zone=java.time.ZoneId.of("Asia/Shanghai");var date=java.time.LocalDate.now(zone).minusDays(1);
  j.update("UPDATE tri_pool_ledger SET created_at=? WHERE entry_type='FUND'",java.sql.Timestamp.from(date.atTime(12,0).atZone(zone).toInstant()));
  pools.distributeDaily(date);pools.distributeDaily(date);
 }
 Map post(String path,Object body){return http.postForObject(path,body,Map.class);}
 Map data(Map r){assertEquals(200,r.get("code"),String.valueOf(r));return (Map)r.get("data");}
 String create(String amount,String channel,String key){return (String)data(post("/member/recharge/orders",Map.of("amount",amount,"channel",channel,"requestId",key))).get("orderNo");}
 String balance(){return (String)data(http.getForObject("/member/recharge/account",Map.class)).get("balance");}
 Map withdraw(String amount,String key){return post("/member/withdraw/apply",Map.of("amount",amount,"idempotencyKey",key));}
 Map order(String key){var headers=new org.springframework.http.HttpHeaders();headers.set("Idempotency-Key",key);return http.postForObject("/order/generateOrder",new org.springframework.http.HttpEntity<>(Map.of("cartIds",List.of(1),"memberReceiveAddressId",1,"payType",4,"useBalance",true),headers),Map.class);}
 @Test void sandboxRechargeConfirmsOnceAndIsMemberScoped(){
  assertEquals(true,data(http.getForObject("/member/recharge/payment-options",Map.class)).get("sandbox"));
  String no=create("100.10","MOCK","sandbox-recharge-0001");assertEquals(no,create("100.1","MOCK","sandbox-recharge-0001"));assertEquals("0.00",balance());
  assertEquals("SANDBOX_RECHARGE",data(post("/member/recharge/orders/"+no+"/pay",null)).get("type"));
  data(post("/member/recharge/orders/"+no+"/sandbox-confirm",null));data(post("/member/recharge/orders/"+no+"/sandbox-confirm",null));assertEquals("100.10",balance());assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_recharge_ledger",Integer.class));
  var other=new UmsMember();other.setId(2L);when(members.getCurrentMember()).thenReturn(other);assertNotEquals(200,post("/member/recharge/orders/"+no+"/sandbox-confirm",null).get("code"));assertEquals("0.00",balance());
 }
 @Test void rechargeDividendWithdrawAndActualBalanceOrderRemainConsistent(){
  String no=create("100.10","MOCK","sandbox-recharge-0002");data(post("/member/recharge/orders/"+no+"/sandbox-confirm",null));
  fundAndSettle();
  assertEquals(new BigDecimal("500.00"),j.queryForObject("SELECT available_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));assertEquals("100.10",balance());
  var w=data(withdraw("100.00","sandbox-withdraw-001"));assertEquals(3,w.get("status"));assertEquals("199.50",balance());data(withdraw("100.00","sandbox-withdraw-001"));assertEquals("199.50",balance());
  var created=data(order("sandbox-order-00001"));assertEquals(true,created.get("paidImmediately"));assertEquals(1,j.queryForObject("SELECT status FROM qa_order",Integer.class));assertEquals("119.50",balance());data(order("sandbox-order-00001"));assertEquals("119.50",balance());
  j.update("UPDATE qa_stock SET price=120 WHERE id=1");assertNotEquals(200,order("sandbox-order-00002").get("code"));assertEquals("119.50",balance());assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM qa_order",Integer.class));assertEquals(9,j.queryForObject("SELECT stock FROM qa_stock",Integer.class));
  assertNotEquals(200,withdraw("401","sandbox-withdraw-002").get("code"));assertEquals(new BigDecimal("400.00"),j.queryForObject("SELECT available_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));assertEquals(new BigDecimal("0.00"),j.queryForObject("SELECT frozen_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));
 }
 @Test void productionRejectsMockAndCannotConfirmRealOrders(){
  String no=create("5","MOCK","sandbox-recharge-0003");ReflectionTestUtils.setField(recharge,"sandboxEnabled",false);
  assertNotEquals(200,post("/member/recharge/orders/"+no+"/sandbox-confirm",null).get("code"));assertNotEquals(200,post("/member/recharge/orders",Map.of("amount","5","channel","MOCK","requestId","sandbox-recharge-0004")).get("code"));assertEquals("0.00",balance());
  ReflectionTestUtils.setField(recharge,"sandboxEnabled",true);j.update("UPDATE ums_recharge_order SET channel='WECHAT' WHERE order_no=?",no);assertNotEquals(200,post("/member/recharge/orders/"+no+"/sandbox-confirm",null).get("code"));
 }
 @Test void ledgerFailureRollsBackBothRechargeAndEarningsTransfer(){
  String no=create("5","MOCK","sandbox-recharge-0005");fundAndSettle();j.execute("DROP TABLE ums_recharge_ledger");
  assertNotEquals(200,post("/member/recharge/orders/"+no+"/sandbox-confirm",null).get("code"));assertEquals("0.00",balance());assertEquals("PENDING",j.queryForObject("SELECT status FROM ums_recharge_order WHERE order_no=?",String.class,no));
  assertNotEquals(200,withdraw("100","sandbox-withdraw-003").get("code"));assertEquals("0.00",balance());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM ums_withdraw_apply",Integer.class));assertEquals(new BigDecimal("500.00"),j.queryForObject("SELECT available_earnings FROM ums_earnings WHERE member_id=1",BigDecimal.class));
 }
 @Test void concurrentRechargeAndWithdrawalRetriesNeverCreditTwice()throws Exception {
  String no=create("10","MOCK","sandbox-recharge-0006");fundAndSettle();
  var executor=Executors.newFixedThreadPool(6);try {
   var jobs=new ArrayList<Future<?>>();for(int i=0;i<6;i++)jobs.add(executor.submit(()->data(post("/member/recharge/orders/"+no+"/sandbox-confirm",null))));for(var job:jobs)job.get(30,TimeUnit.SECONDS);
   jobs.clear();for(int i=0;i<6;i++)jobs.add(executor.submit(()->data(withdraw("10","sandbox-withdraw-004"))));for(var job:jobs)job.get(30,TimeUnit.SECONDS);
  }finally{executor.shutdownNow();}
  assertEquals("19.94",balance());assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM ums_withdraw_apply",Integer.class));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM ums_recharge_ledger",Integer.class));
 }
}
