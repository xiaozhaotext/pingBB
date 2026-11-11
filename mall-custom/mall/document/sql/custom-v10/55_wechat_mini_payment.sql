-- Mini-program identities are scoped to the AppID, independently of public-account OAuth.
CREATE TABLE IF NOT EXISTS ums_member_wechat_identity (
    app_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    member_id BIGINT NOT NULL,
    open_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (app_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Keep existing account switching behavior. Authorization tickets remain member-scoped.
SET @mini_identity_unique = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='ums_member_wechat_identity'
      AND index_name='uk_wechat_mini_open_id' AND non_unique=0);
SET @mini_identity_ddl = IF(@mini_identity_unique>0,
    'ALTER TABLE ums_member_wechat_identity DROP INDEX uk_wechat_mini_open_id', 'SELECT 1');
PREPARE mini_identity_stmt FROM @mini_identity_ddl;
EXECUTE mini_identity_stmt;
DEALLOCATE PREPARE mini_identity_stmt;

-- Separate provider trade numbers allow an existing web order to be paid in the mini program.
CREATE TABLE IF NOT EXISTS oms_wechat_mini_payment (
    order_id BIGINT NOT NULL,
    order_sn VARCHAR(64) NOT NULL,
    out_trade_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    app_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    open_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_mini_payment_trade (out_trade_no),
    UNIQUE KEY uk_mini_payment_order_sn (order_sn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
