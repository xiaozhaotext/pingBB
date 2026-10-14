-- 35_sms_log.sql
-- 短信发送记录表 sys_sms_log
-- 独立新增表，可重复执行（IF NOT EXISTS），无危险语句

CREATE TABLE IF NOT EXISTS `sys_sms_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(20) NOT NULL COMMENT '接收手机号',
  `template_code` varchar(64) DEFAULT NULL COMMENT '短信模板 Code',
  `content` varchar(500) DEFAULT NULL COMMENT '发送内容摘要',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待发送/SUCCESS成功/FAILED失败/CONFIG_CHECK配置检查/SKIPPED跳过',
  `response` varchar(1000) DEFAULT NULL COMMENT '服务商返回或备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sms_log_phone` (`phone`),
  KEY `idx_sms_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录表';
