package com.macro.mall.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Platform-admin-only reassignment of a member's direct parent and descendant paths. */
@Service
public class P0ReferralAdminService {
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${mall.membership-v2.enabled:false}")
    private boolean membershipV2Enabled;

    public P0ReferralAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reassign(long memberId, long newInviterId, long operatorId,
                         String operatorName, String reason) {
        if (membershipV2Enabled && !jdbc.queryForList("SELECT s.order_id FROM tri_order_snapshot s JOIN oms_order o ON o.id=s.order_id WHERE s.member_id=? AND o.status IN (0,1,2,3) FOR UPDATE",Long.class,memberId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"该用户已有会员身份牌支付快照，调整邀请关系需专门核对历史奖励");
        if (memberId <= 0 || newInviterId <= 0 || memberId == newInviterId
                || reason == null || reason.trim().length() < 5 || reason.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会员、推荐人或原因无效");
        }
        List<Long> members = jdbc.queryForList(
                "SELECT id FROM ums_member WHERE id IN (?,?) ORDER BY id FOR UPDATE",
                Long.class, memberId, newInviterId);
        if (members.size() != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会员或推荐人不存在");

        List<Long> old = jdbc.queryForList(
                "SELECT inviter_id FROM ums_member_recommend WHERE member_id=? FOR UPDATE",
                Long.class, memberId);
        Long oldInviterId = old.isEmpty() ? null : old.get(0);
        if (oldInviterId != null && oldInviterId == newInviterId) return;

        // Walk the proposed parent's real edges, not its cached bind_path.
        List<Long> ancestors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long current = newInviterId;
        while (current != null) {
            if (current == memberId || !seen.add(current)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "推荐关系会形成循环");
            }
            ancestors.add(current);
            List<Long> parent = jdbc.queryForList(
                    "SELECT inviter_id FROM ums_member_recommend WHERE member_id=? FOR UPDATE",
                    Long.class, current);
            current = parent.isEmpty() ? null : parent.get(0);
        }
        Collections.reverse(ancestors);
        StringBuilder pathBuilder = new StringBuilder();
        for (Long id : ancestors) pathBuilder.append('/').append(id);
        String newPath = checkedPath(pathBuilder + "/" + memberId);

        if (old.isEmpty()) {
            jdbc.update("INSERT INTO ums_member_recommend(member_id,inviter_id,bind_path) VALUES(?,?,?)",
                    memberId, newInviterId, newPath);
        } else {
            jdbc.update("UPDATE ums_member_recommend SET inviter_id=?,invite_code=NULL,bind_order_id=NULL,"
                            + "bind_path=?,bind_time=CURRENT_TIMESTAMP WHERE member_id=?",
                    newInviterId, newPath, memberId);
        }

        // Cached paths of all descendants must follow the newly assigned branch.
        ArrayDeque<PathNode> queue = new ArrayDeque<>();
        Set<Long> descendants = new HashSet<>();
        descendants.add(memberId);
        queue.add(new PathNode(memberId, newPath));
        while (!queue.isEmpty()) {
            PathNode node = queue.removeFirst();
            List<Long> children = jdbc.queryForList(
                    "SELECT member_id FROM ums_member_recommend WHERE inviter_id=? ORDER BY member_id FOR UPDATE",
                    Long.class, node.memberId());
            for (Long child : children) {
                if (!descendants.add(child)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "原推荐树存在循环");
                }
                String childPath = checkedPath(node.path() + "/" + child);
                jdbc.update("UPDATE ums_member_recommend SET bind_path=? WHERE member_id=?", childPath, child);
                queue.addLast(new PathNode(child, childPath));
            }
        }

        String previous = oldInviterId == null ? "未绑定" : oldInviterId.toString();
        String detail = "推荐人 " + previous + " -> " + newInviterId + "；原因：" + reason.trim();
        jdbc.update("INSERT INTO ums_member_recommend_audit(member_id,inviter_id,action,operator_id,detail)"
                        + " VALUES(?,?,'admin_reassign',?,?)",
                memberId, newInviterId, operatorId, detail);
        jdbc.update("INSERT INTO sys_audit_log(operator_id,operator_name,module,action,target_id,detail,"
                        + "`operator`,target_type,old_value,new_value)"
                        + " VALUES(?,?,'member','referrer_reassign',?,?,?,?,?,?)",
                operatorId, operatorName, memberId, detail, operatorName,
                "ums_member_recommend", previous, Long.toString(newInviterId));
    }

    private String checkedPath(String path) {
        if (path.length() > 512) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "推荐链过深，超过数据库字段长度");
        }
        return path;
    }

    private record PathNode(long memberId, String path) { }
}
