-- 渠道退款记录 2026-10-13：订单退货完成时调用真实支付渠道退款
--
-- 对应需求：真实上线所需的"支付/退款"闭环（普通订单支付宝退款）。
-- 只做新增：CREATE TABLE IF NOT EXISTS，可重复执行，不含删表语句。
-- 执行：mysql mall_demo < 27_order_refund_record.sql

CREATE TABLE IF NOT EXISTS `oms_refund_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_apply_id` bigint NOT NULL COMMENT '退货申请单ID',
  `order_id` bigint NOT NULL,
  `channel_code` varchar(24) NOT NULL COMMENT '退款渠道：ALIPAY 等',
  `channel_trade_no` varchar(128) DEFAULT NULL COMMENT '渠道交易号',
  `out_request_no` varchar(64) NOT NULL COMMENT '退款请求号，保证渠道侧幂等',
  `refund_amount` decimal(18,2) NOT NULL COMMENT '退款金额',
  `state` varchar(24) NOT NULL COMMENT 'SUCCESS 已退款 / FAILED 退款失败待人工 / SKIPPED 渠道未启用',
  `channel_refund_no` varchar(128) DEFAULT NULL COMMENT '渠道退款单号',
  `error_msg` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_apply` (`return_apply_id`),
  UNIQUE KEY `uk_refund_out_request` (`out_request_no`),
  KEY `idx_refund_order` (`order_id`),
  CONSTRAINT `fk_refund_record_order` FOREIGN KEY (`order_id`) REFERENCES `oms_order` (`id`),
  CONSTRAINT `ck_refund_record_amount` CHECK (`refund_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道退款记录';