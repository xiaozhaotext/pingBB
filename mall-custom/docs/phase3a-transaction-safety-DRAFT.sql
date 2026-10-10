-- PRODUCTION REVIEW DRAFT ONLY. The four additive tables were executed in the
-- dedicated local mall_phase3a test database after user approval on 2026-10-08.
-- This file has NOT been applied to production or a multi-merchant database.
-- A repeatable production migration, rollback plan and approval remain required.

CREATE TABLE oms_order_request (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  order_id BIGINT NULL,
  state VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_order_request_member_key (member_id, idempotency_key),
  KEY idx_order_request_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oms_payment_exception (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_sn VARCHAR(64) NOT NULL,
  channel_code VARCHAR(32) NOT NULL,
  channel_trade_no VARCHAR(128) NOT NULL,
  paid_amount DECIMAL(18,2) NOT NULL,
  currency_code CHAR(3) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  handled_at DATETIME(6) NULL,
  UNIQUE KEY uk_payment_exception_trade (channel_code, channel_trade_no),
  KEY idx_payment_exception_order_state (order_id, state, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oms_payment_confirmation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  channel_code VARCHAR(32) NOT NULL,
  channel_trade_no VARCHAR(128) NOT NULL,
  paid_amount DECIMAL(18,2) NOT NULL,
  currency_code CHAR(3) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_payment_confirmation_order (order_id),
  UNIQUE KEY uk_payment_confirmation_trade (channel_code, channel_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oms_refund_compensation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_exception_id BIGINT NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'WAITING_MANUAL_REVIEW',
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_refund_compensation_exception (payment_exception_id),
  KEY idx_refund_compensation_state (state, created_at),
  CONSTRAINT fk_refund_compensation_exception FOREIGN KEY (payment_exception_id) REFERENCES oms_payment_exception(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Activation remains blocked: full HTTP order replay, signed notification,
-- channel query/close, automated refund, reconciliation and compensation retry
-- were not end-to-end verified. See Phase3A最终运行验收报告.md.
