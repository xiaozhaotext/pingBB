package com.macro.mall.portal.service;

import com.macro.mall.portal.dao.UmsMemberIdentityLogMapper;
import com.macro.mall.portal.dao.UmsMemberIdentityMapper;
import com.macro.mall.portal.dao.UmsPointsAccountMapper;
import com.macro.mall.portal.dao.UmsPointsLedgerMapper;
import com.macro.mall.portal.service.impl.MemberIdentityServiceImpl;
import com.macro.mall.portal.service.impl.PointsWalletServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requires an isolated MySQL 8 schema containing ums_member, the existing identity/point
 * tables, and 12_membership_real_payment.sql. Never point this test at a live database.
 */
class MembershipVerifiedPaymentHandlerMysqlTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private MembershipVerifiedPaymentHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("MALL_MEMBERSHIP_TEST_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "isolated MySQL URL not supplied");
        assertTrue(url.contains("127.0.0.1") || url.contains("localhost"), "test DB must be local");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("MALL_MEMBERSHIP_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("MALL_MEMBERSHIP_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        SqlSessionTemplate sqlSession = sqlSession(dataSource);
        PointsWalletServiceImpl points = new PointsWalletServiceImpl();
        ReflectionTestUtils.setField(points, "accountMapper", sqlSession.getMapper(UmsPointsAccountMapper.class));
        ReflectionTestUtils.setField(points, "ledgerMapper", sqlSession.getMapper(UmsPointsLedgerMapper.class));
        MemberIdentityServiceImpl identities = new MemberIdentityServiceImpl();
        ReflectionTestUtils.setField(identities, "identityMapper", sqlSession.getMapper(UmsMemberIdentityMapper.class));
        ReflectionTestUtils.setField(identities, "identityLogMapper", sqlSession.getMapper(UmsMemberIdentityLogMapper.class));
        MembershipReferralRewardService referral = new MembershipReferralRewardService(jdbc, points, new MemberEarningsCreditService(jdbc));
        handler = new MembershipVerifiedPaymentHandler(jdbc, points, identities, referral);
    }

    @Test
    void verifiedPurchasePostsOnceAndWritesAllLedgers() {
        long memberId = member();
        long orderId = order(memberId, "PARTNER", "3999.00", 3999);
        var payment = payment(orderId, "3999.00");

        transaction.executeWithoutResult(status -> handler.accept(payment));
        transaction.executeWithoutResult(status -> handler.accept(payment));

        assertEquals("PAID", text("SELECT status FROM membership_purchase_order WHERE id=?", orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM membership_purchase_order WHERE id=? AND transaction_id=?", orderId, payment.transactionId()));
        assertEquals(3999, count("SELECT balance FROM ums_points_account WHERE member_id=? AND points_type=1", memberId));
        assertEquals(3999, count("SELECT balance FROM ums_points_account WHERE member_id=? AND points_type=2", memberId));
        assertEquals(2, count("SELECT COUNT(*) FROM ums_points_ledger WHERE member_id=?", memberId));
        assertEquals(0, count("SELECT COUNT(*) FROM member_point_ledger WHERE member_id=?", memberId)); // 已统一到 ums_points_ledger，不再双写
        assertEquals(1, count("SELECT identity_type FROM ums_member_identity WHERE member_id=?", memberId));
        assertEquals(1, count("SELECT COUNT(*) FROM member_level_change_log WHERE order_id=?", orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_audit_log WHERE target_type='membership_purchase_order' AND target_id=?", orderId));
    }

    @Test
    void lateAuditFailureRollsBackOrderPointsAndIdentity() {
        long memberId = member();
        long orderId = order(memberId, "COFOUNDER", "10000.00", 10000);
        // Force the final identity audit INSERT to fail after the order, both wallets and identity changed.
        jdbc.update("INSERT INTO member_level_change_log(member_id,order_id,from_level,to_level,`operator`,reason) VALUES(?,?,'NORMAL','COFOUNDER','test','rollback probe')", memberId, orderId);

        assertThrows(DuplicateKeyException.class,
                () -> transaction.executeWithoutResult(status -> handler.accept(payment(orderId, "10000.00"))));
        assertEquals("PENDING", text("SELECT status FROM membership_purchase_order WHERE id=?", orderId));
        assertEquals(0, count("SELECT COUNT(*) FROM ums_points_account WHERE member_id=?", memberId));
        assertEquals(0, count("SELECT COUNT(*) FROM ums_points_ledger WHERE member_id=?", memberId));
        assertEquals(0, count("SELECT COUNT(*) FROM ums_member_identity WHERE member_id=?", memberId));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_audit_log WHERE target_type='membership_purchase_order' AND target_id=?", orderId));
    }

    @Test
    void cofounderReferralCreditsCappedCashAndConfiguredRepurchasePointsOnce() {
        long referrerId = member();
        long buyerId = member();
        jdbc.update("INSERT INTO ums_member_recommend(member_id,inviter_id,bind_path) VALUES(?,?,?)",
                buyerId, referrerId, "/" + referrerId + "/" + buyerId);
        jdbc.update("UPDATE sys_config SET config_value='1000' WHERE config_key='membership.referral.cofounder.repurchase_points'");
        PointsWalletService wallet = (PointsWalletService) ReflectionTestUtils.getField(handler, "pointsWallet");
        MembershipReferralRewardService rewards = new MembershipReferralRewardService(jdbc, wallet, new MemberEarningsCreditService(jdbc));
        var snapshot = rewards.snapshotFor(buyerId, "COFOUNDER");
        assertNotNull(snapshot);
        long orderId = order(buyerId, "COFOUNDER", "10000.00", 10000);
        jdbc.update("UPDATE membership_purchase_order SET referrer_member_id=?,referral_cash_rate=?,"
                        + "referral_cash_cap=?,referral_repurchase_points=? WHERE id=?",
                snapshot.referrerMemberId(), snapshot.cashRate(), snapshot.cashCap(), snapshot.repurchasePoints(), orderId);
        var verified = payment(orderId, "10000.00");

        transaction.executeWithoutResult(status -> handler.accept(verified));
        transaction.executeWithoutResult(status -> handler.accept(verified));

        assertEquals(new BigDecimal("200.00"), jdbc.queryForObject(
                "SELECT available_earnings FROM ums_earnings WHERE member_id=?", BigDecimal.class, referrerId));
        assertEquals(1, count("SELECT COUNT(*) FROM ums_earnings_ledger WHERE member_id=? AND change_type='referral_reward'", referrerId));
        assertEquals(1000, count("SELECT balance FROM ums_points_account WHERE member_id=? AND points_type=2", referrerId));
        assertEquals(0, count("SELECT COUNT(*) FROM member_cash_account WHERE member_id=?", referrerId)); // 第二套账本已弃用
        assertEquals(1, count("SELECT COUNT(*) FROM member_referral_reward WHERE order_id=? AND reward_amount=200.00 AND reward_points=1000", orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_audit_log WHERE target_type='member_referral_reward' AND target_id=?", orderId));
    }

    private SqlSessionTemplate sqlSession(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.afterPropertiesSet();
        SqlSessionFactory factory = bean.getObject();
        assertNotNull(factory);
        factory.getConfiguration().setMapUnderscoreToCamelCase(true);
        factory.getConfiguration().addMapper(UmsPointsAccountMapper.class);
        factory.getConfiguration().addMapper(UmsPointsLedgerMapper.class);
        factory.getConfiguration().addMapper(UmsMemberIdentityMapper.class);
        factory.getConfiguration().addMapper(UmsMemberIdentityLogMapper.class);
        return new SqlSessionTemplate(factory);
    }

    private long member() {
        long id = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
        jdbc.update("INSERT INTO ums_member(id) VALUES(?)", id);
        return id;
    }

    private long order(long memberId, String level, String amount, long points) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO membership_purchase_order(merchant_order_no,request_id,member_id,level,amount,product_points,repurchase_points,pay_channel,pay_mode,status) VALUES(?,?,?,?,?,?,?,'ALIPAY','ALIPAY_WAP','PENDING')",
                "TEST" + suffix, suffix, memberId, level, new BigDecimal(amount), points, points);
        return jdbc.queryForObject("SELECT id FROM membership_purchase_order WHERE merchant_order_no=?", Long.class, "TEST" + suffix);
    }

    private MembershipPaymentGateway.VerifiedPayment payment(long orderId, String amount) {
        String no = text("SELECT merchant_order_no FROM membership_purchase_order WHERE id=?", orderId);
        return new MembershipPaymentGateway.VerifiedPayment(no, "TX" + UUID.randomUUID().toString().replace("-", ""),
                "ALIPAY", new BigDecimal(amount));
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
