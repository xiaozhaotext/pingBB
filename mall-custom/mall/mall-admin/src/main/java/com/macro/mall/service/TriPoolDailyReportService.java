package com.macro.mall.service;

import com.macro.mall.common.membership.TriPoolRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class TriPoolDailyReportService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    public TriPoolDailyReportService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public LocalDate day(LocalDate requested) { return requested==null?LocalDate.now(ZONE):requested; }
    public Timestamp start(LocalDate day) { return Timestamp.from(day.atStartOfDay(ZONE).toInstant()); }

    /** Historical balances come from the ledger, never from today's current pool balance. */
    @Transactional(readOnly=true)
    public Map<String,Object> daily(LocalDate requested) {
        LocalDate date=day(requested);
        Timestamp from=start(date),to=start(date.plusDays(1));
        var rows=new ArrayList<Map<String,Object>>();
        for(var pool:TriPoolRules.Pool.values()) {
            var amounts=jdbc.queryForMap(
                    "SELECT COALESCE(SUM(CASE WHEN created_at<? THEN amount ELSE 0 END),0) AS opening,"+
                    "COALESCE(SUM(CASE WHEN created_at>=? AND entry_type='FUND' THEN amount ELSE 0 END),0) AS funded,"+
                    "COALESCE(-SUM(CASE WHEN created_at>=? AND entry_type='DISTRIBUTE' THEN amount ELSE 0 END),0) AS distributed,"+
                    "COALESCE(SUM(amount),0) AS closing,COUNT(CASE WHEN created_at>=? THEN 1 END) AS `ledgerCount` "+
                    "FROM tri_pool_ledger WHERE pool_code=? AND created_at<?",from,from,from,from,pool.name(),to);
            var row=new LinkedHashMap<String,Object>();
            for(String key:List.of("opening","funded","distributed","closing","ledgerCount"))row.put(key,amounts.get(key));
            row.put("code",pool.name());
            var counts=jdbc.queryForMap("SELECT COUNT(*) AS `awardCount`,COUNT(DISTINCT member_id) AS `recipientCount` "+
                    "FROM tri_pool_distribution WHERE pool_code=? AND created_at>=? AND created_at<?",pool.name(),from,to);
            row.put("awardCount",counts.get("awardCount"));row.put("recipientCount",counts.get("recipientCount"));
            row.put("batchCount",jdbc.queryForObject("SELECT COUNT(*) FROM tri_pool_batch WHERE pool_code=? AND paid_total>0 AND created_at>=? AND created_at<?",Long.class,pool.name(),from,to));
            BigDecimal opening=new BigDecimal(row.get("opening").toString()),funded=new BigDecimal(row.get("funded").toString()),
                    distributed=new BigDecimal(row.get("distributed").toString()),closing=new BigDecimal(row.get("closing").toString());
            if(opening.add(funded).subtract(distributed).compareTo(closing)!=0)throw new IllegalStateException("分红池日账不平，请核对资金流水");
            rows.add(row);
        }
        return Map.of("date",date.toString(),"timezone",ZONE.getId(),"pools",rows);
    }
}
