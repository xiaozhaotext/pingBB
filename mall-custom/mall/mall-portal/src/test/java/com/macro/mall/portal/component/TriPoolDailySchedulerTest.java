package com.macro.mall.portal.component;

import com.macro.mall.common.membership.TriPoolService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TriPoolDailySchedulerTest {
    @Test void runsAtBeijingMidnightAndSettlesPreviousDay() throws Exception {
        var annotation=TriPoolDailyScheduler.class.getMethod("runDaily").getAnnotation(Scheduled.class);
        assertEquals("0 0 0 * * *",annotation.cron());assertEquals("Asia/Shanghai",annotation.zone());
        var pools=mock(TriPoolService.class);when(pools.enabled()).thenReturn(true);
        when(pools.distributeDaily(any())).thenReturn(List.of());
        var job=new TriPoolDailyScheduler(pools);ReflectionTestUtils.setField(job,"enabled",true);
        job.runDaily();verify(pools).distributeDaily(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
    }
    @Test void disabledRuleOrScheduleCannotPay() {
        var pools=mock(TriPoolService.class);var job=new TriPoolDailyScheduler(pools);
        ReflectionTestUtils.setField(job,"enabled",false);job.runDaily();verifyNoInteractions(pools);
        ReflectionTestUtils.setField(job,"enabled",true);job.runDaily();verify(pools,never()).distributeDaily(any());
    }
}
