package com.macro.mall.portal.service;

import com.macro.mall.portal.dao.UmsMemberRecommendMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InvitationTierMapperTest {
    @Test void tenInvitationsIncludeNinePaidMembersAndAnExplicitVisitorWithoutLeakingAnotherInviter() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:invite_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","");
        // Keep one connection alive across mapper calls.
        try(var hold=ds.getConnection()) {
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE ums_member(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE ums_member_recommend(id BIGINT PRIMARY KEY,member_id BIGINT,inviter_id BIGINT)");
            jdbc.execute("CREATE TABLE tri_member(member_id BIGINT PRIMARY KEY,tier VARCHAR(20))");
            jdbc.execute("CREATE TABLE ums_member_identity(member_id BIGINT PRIMARY KEY,identity_type INT)");
            for(long id=100;id<110;id++) {
                jdbc.update("INSERT INTO ums_member VALUES(?)",id);
                jdbc.update("INSERT INTO ums_member_recommend VALUES(?,?,97)",id,id);
                if(id!=102)jdbc.update("INSERT INTO tri_member VALUES(?,'MEMBER')",id);
            }
            jdbc.update("INSERT INTO ums_member VALUES(999)");
            jdbc.update("INSERT INTO ums_member_recommend VALUES(999,999,98)");
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);config.addMapper(UmsMemberRecommendMapper.class);
            var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
            try(var session=factory.getObject().openSession()) {
                var mapper=session.getMapper(UmsMemberRecommendMapper.class);
                var rows=mapper.selectInviteesWithTier(97L);
                assertEquals(10,rows.size());
                assertEquals(9,rows.stream().filter(r->"MEMBER".equals(r.getMembershipTier())).count());
                assertEquals("VISITOR",rows.stream().filter(r->r.getMemberId()==102L).findFirst().orElseThrow().getMembershipTier());
                jdbc.update("INSERT INTO ums_member_identity VALUES(102,2)");
                assertEquals("VISITOR",mapper.selectInviteesWithTier(97L).stream().filter(r->r.getMemberId()==102L).findFirst().orElseThrow().getMembershipTier());
                assertEquals("COFOUNDER",mapper.selectLegacyInviteesWithTier(97L).stream().filter(r->r.getMemberId()==102L).findFirst().orElseThrow().getMembershipTier());
            }
        }
    }
}
