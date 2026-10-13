package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberBrandAttention;
import com.macro.mall.portal.service.MemberAttentionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * 会员关注Service实现类（改为 MySQL 存储，不再依赖 MongoDB）
 * Created by macro on 2018/8/2.
 */
@Service
public class MemberAttentionServiceImpl implements MemberAttentionService {
    private static final String COLUMNS = "id,member_id,member_nickname,member_icon,brand_id,brand_name,brand_logo,brand_city,create_time";

    private static final RowMapper<MemberBrandAttention> ROW_MAPPER = (rs, rowNum) -> {
        MemberBrandAttention item = new MemberBrandAttention();
        item.setId(String.valueOf(rs.getLong("id")));
        item.setMemberId(rs.getLong("member_id"));
        item.setMemberNickname(rs.getString("member_nickname"));
        item.setMemberIcon(rs.getString("member_icon"));
        item.setBrandId(rs.getLong("brand_id"));
        item.setBrandName(rs.getString("brand_name"));
        item.setBrandLogo(rs.getString("brand_logo"));
        item.setBrandCity(rs.getString("brand_city"));
        Timestamp createTime = rs.getTimestamp("create_time");
        item.setCreateTime(createTime == null ? null : new Date(createTime.getTime()));
        return item;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PmsBrandMapper brandMapper;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int add(MemberBrandAttention memberBrandAttention) {
        if (memberBrandAttention == null || memberBrandAttention.getBrandId() == null) {
            return 0;
        }
        PmsBrand brand = brandMapper.selectByPrimaryKey(memberBrandAttention.getBrandId());
        if (brand == null) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ums_member_brand_attention WHERE member_id=? AND brand_id=?",
                Integer.class, member.getId(), brand.getId());
        if (exists != null && exists > 0) {
            return 0;
        }
        return jdbcTemplate.update(
                "INSERT INTO ums_member_brand_attention (member_id,member_nickname,member_icon,brand_id,brand_name,brand_logo,brand_city,create_time) VALUES (?,?,?,?,?,?,?,?)",
                member.getId(), member.getNickname(), member.getIcon(), brand.getId(), brand.getName(), brand.getLogo(), null,
                new Timestamp(System.currentTimeMillis()));
    }

    @Override
    public int delete(Long brandId) {
        if (brandId == null) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        return jdbcTemplate.update("DELETE FROM ums_member_brand_attention WHERE member_id=? AND brand_id=?",
                member.getId(), brandId);
    }

    @Override
    public Page<MemberBrandAttention> list(Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 5 : pageSize;
        UmsMember member = memberService.getCurrentMember();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ums_member_brand_attention WHERE member_id=?", Long.class, member.getId());
        List<MemberBrandAttention> content = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM ums_member_brand_attention WHERE member_id=? ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, member.getId(), size, (num - 1) * size);
        return new PageImpl<>(content, PageRequest.of(num - 1, size), total == null ? 0L : total);
    }

    @Override
    public MemberBrandAttention detail(Long brandId) {
        if (brandId == null) {
            return null;
        }
        UmsMember member = memberService.getCurrentMember();
        List<MemberBrandAttention> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM ums_member_brand_attention WHERE member_id=? AND brand_id=?",
                ROW_MAPPER, member.getId(), brandId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public void clear() {
        UmsMember member = memberService.getCurrentMember();
        jdbcTemplate.update("DELETE FROM ums_member_brand_attention WHERE member_id=?", member.getId());
    }
}
