package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.MembershipTierService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Member travel route requests; the request itself does not promise a booking. */
@RestController
@RequestMapping("/member/travel/custom")
public class MemberCustomTravelController {
    private final JdbcTemplate jdbc;
    private final UmsMemberService members;
    private final MembershipTierService tiers;

    public MemberCustomTravelController(JdbcTemplate jdbc, UmsMemberService members,
                                        MembershipTierService tiers) {
        this.jdbc = jdbc;
        this.members = members;
        this.tiers = tiers;
    }

    public record Request(String destination, LocalDate departureDate, Integer travelerCount,
                          String requirements) { }

    @GetMapping
    public CommonResult<List<Map<String, Object>>> mine() {
        long memberId = members.getCurrentMember().getId();
        if (!tiers.isPaidMember(memberId)) return CommonResult.forbidden(null);
        return CommonResult.success(jdbc.queryForList("SELECT id,destination,departure_date,traveler_count,"
                + "requirements,status,admin_note,create_time,update_time FROM member_custom_travel_request"
                + " WHERE member_id=? ORDER BY id DESC LIMIT 50", memberId));
    }

    @PostMapping
    public CommonResult<Void> submit(@RequestBody Request request) {
        long memberId = members.getCurrentMember().getId();
        if (!tiers.isPaidMember(memberId)) return CommonResult.forbidden(null);
        if (request == null || request.destination() == null
                || request.destination().trim().length() < 2 || request.destination().trim().length() > 100
                || request.travelerCount() == null || request.travelerCount() < 1 || request.travelerCount() > 50
                || (request.departureDate() != null && request.departureDate().isBefore(
                        LocalDate.now(ZoneId.of("Asia/Shanghai"))))
                || (request.requirements() != null && request.requirements().length() > 2000)) {
            return CommonResult.validateFailed("请填写目的地（2-100字）、出行人数（1-50人）及有效出发日期");
        }
        jdbc.update("INSERT INTO member_custom_travel_request"
                        + "(member_id,destination,departure_date,traveler_count,requirements,status)"
                        + " VALUES(?,?,?,?,?,'SUBMITTED')", memberId, request.destination().trim(),
                request.departureDate(), request.travelerCount(), request.requirements());
        return CommonResult.success(null);
    }
}
