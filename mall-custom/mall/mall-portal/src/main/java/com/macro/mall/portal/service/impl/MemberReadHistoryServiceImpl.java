package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.MemberReadHistory;
import com.macro.mall.portal.service.MemberReadHistoryService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 会员浏览记录管理Service实现类（改为 MySQL 存储，不再依赖 MongoDB）
 * Created by macro on 2018/8/3.
 */
@Service
public class MemberReadHistoryServiceImpl implements MemberReadHistoryService {
    private static final String COLUMNS = "id,member_id,member_nickname,member_icon,product_id,product_name,product_pic,product_sub_title,product_price,create_time";

    private static final RowMapper<MemberReadHistory> ROW_MAPPER = (rs, rowNum) -> {
        MemberReadHistory item = new MemberReadHistory();
        item.setId(String.valueOf(rs.getLong("id")));
        item.setMemberId(rs.getLong("member_id"));
        item.setMemberNickname(rs.getString("member_nickname"));
        item.setMemberIcon(rs.getString("member_icon"));
        item.setProductId(rs.getLong("product_id"));
        item.setProductName(rs.getString("product_name"));
        item.setProductPic(rs.getString("product_pic"));
        item.setProductSubTitle(rs.getString("product_sub_title"));
        BigDecimal price = rs.getBigDecimal("product_price");
        item.setProductPrice(price == null ? null : price.toPlainString());
        Timestamp createTime = rs.getTimestamp("create_time");
        item.setCreateTime(createTime == null ? null : new Date(createTime.getTime()));
        return item;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int create(MemberReadHistory memberReadHistory) {
        if (memberReadHistory == null || memberReadHistory.getProductId() == null) {
            return 0;
        }
        PmsProduct product = productMapper.selectByPrimaryKey(memberReadHistory.getProductId());
        if (product == null || Integer.valueOf(1).equals(product.getDeleteStatus())) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        return jdbcTemplate.update(
                "INSERT INTO ums_member_read_history (member_id,member_nickname,member_icon,product_id,product_name,product_pic,product_sub_title,product_price,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                member.getId(), member.getNickname(), member.getIcon(), product.getId(), product.getName(), product.getPic(),
                product.getSubTitle(), product.getPrice(), new Timestamp(System.currentTimeMillis()));
    }

    @Override
    public int delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> numericIds = new ArrayList<>();
        for (String id : ids) {
            if (id == null) {
                continue;
            }
            try {
                numericIds.add(Long.valueOf(id.trim()));
            } catch (NumberFormatException ignored) {
                // 非数字 id 无法在 MySQL 中定位，跳过而不是抛异常
            }
        }
        if (numericIds.isEmpty()) {
            return 0;
        }
        UmsMember member = memberService.getCurrentMember();
        String placeholders = String.join(",", Collections.nCopies(numericIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(member.getId());
        args.addAll(numericIds);
        return jdbcTemplate.update("DELETE FROM ums_member_read_history WHERE member_id=? AND id IN (" + placeholders + ")", args.toArray());
    }

    @Override
    public Page<MemberReadHistory> list(Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 5 : pageSize;
        UmsMember member = memberService.getCurrentMember();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ums_member_read_history WHERE member_id=?", Long.class, member.getId());
        List<MemberReadHistory> content = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM ums_member_read_history WHERE member_id=? ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, member.getId(), size, (num - 1) * size);
        return new PageImpl<>(content, PageRequest.of(num - 1, size), total == null ? 0L : total);
    }

    @Override
    public void clear() {
        UmsMember member = memberService.getCurrentMember();
        jdbcTemplate.update("DELETE FROM ums_member_read_history WHERE member_id=?", member.getId());
    }
}
