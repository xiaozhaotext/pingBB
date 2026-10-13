package com.macro.mall.portal.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 记录消息发送结果，供后台“发送记录”查看。写日志失败绝不能影响会员主流程。 */
@Component
public class MessageRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageRecorder.class);
    private final JdbcTemplate jdbc;

    public MessageRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String channel, String target, String content, String bizType, String bizId,
                       String status, String errorMsg) {
        try {
            jdbc.update("INSERT INTO sys_message_record(channel,target,content,biz_type,biz_id,status,error_msg)"
                            + " VALUES(?,?,?,?,?,?,?)",
                    channel, target, content, bizType, bizId, status, errorMsg);
        } catch (RuntimeException e) {
            LOGGER.warn("message record skipped: {}", e.getMessage());
        }
    }
}
