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

class TriPoolWeeklySchedulerTest {
    @Test void cronRunsOnlyOnMondayIncludingYearBoundary() {
        var cron=org.springframework.scheduling.support.CronExpression.parse("0 0 0 * * MON");
        var zone=ZoneId.of("Asia/Shanghai");
        var sunday=java.time.ZonedDateTime.of(2026,11,22,23,59,59,0,zone);
        assertEquals(java.time.ZonedDateTime.of(2026,11,23,0,0,0,0,zone),cron.next(sunday));
        assertEquals(java.time.ZonedDateTime.of(2026,11,30,0,0,0,0,zone),cron.next(cron.next(sunday)));
        assertEquals(java.time.ZonedDateTime.of(2027,1,4,0,0,0,0,zone),
                cron.next(java.time.ZonedDateTime.of(2026,12,31,23,59,59,0,zone)));
    }
    @Test void runsAtBeijingMondayMidnightAndSettlesPreviousWeek() throws Exception {
        var annotation=TriPoolWeeklyScheduler.class.getMethod("runWeekly").getAnnotation(Scheduled.class);
        assertEquals("0 0 0 * * MON",annotation.cron());assertEquals("Asia/Shanghai",annotation.zone());
        var pools=mock(TriPoolService.class);when(pools.enabled()).thenReturn(true);
        when(pools.distributeWeekly(any())).thenReturn(List.of());
        var job=new TriPoolWeeklyScheduler(pools);ReflectionTestUtils.setField(job,"enabled",true);
        job.runWeekly();verify(pools).distributeWeekly(LocalDate.now(ZoneId.of("Asia/Shanghai")).with(java.time.temporal.TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY)));
    }
    @Test void disabledRuleOrScheduleCannotPay() {
        var pools=mock(TriPoolService.class);var job=new TriPoolWeeklyScheduler(pools);
        ReflectionTestUtils.setField(job,"enabled",false);job.runWeekly();verifyNoInteractions(pools);
        ReflectionTestUtils.setField(job,"enabled",true);job.runWeekly();verify(pools,never()).distributeWeekly(any());
    }
}
