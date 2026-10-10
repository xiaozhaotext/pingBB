-- PHASE 3A REVIEW DRAFT ONLY. Do not execute on production or an existing database.
-- Apply only after review, full backup and an empty dedicated MySQL 8 test database.
-- merchant_id is ownership within one marketplace; it is not a SaaS tenant_id.

CREATE TABLE merchant (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  merchant_code VARCHAR(64) NOT NULL,
  merchant_name VARCHAR(200) NOT NULL,
  merchant_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  review_status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_merchant_code (merchant_code),
  CONSTRAINT ck_merchant_type CHECK (merchant_type IN ('PLATFORM_SELF','THIRD_PARTY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchant_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_merchant_user_name (merchant_id, username),
  UNIQUE KEY uk_merchant_user_scope (id, merchant_id),
  CONSTRAINT fk_merchant_user_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchant_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_merchant_role_code (merchant_id, role_code),
  UNIQUE KEY uk_merchant_role_scope (id, merchant_id),
  CONSTRAINT fk_merchant_role_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE merchant_user_role (
  merchant_id BIGINT NOT NULL,
  merchant_user_id BIGINT NOT NULL,
  merchant_role_id BIGINT NOT NULL,
  PRIMARY KEY (merchant_user_id, merchant_role_id),
  KEY idx_merchant_user_role_scope (merchant_id, merchant_role_id),
  CONSTRAINT fk_mur_user FOREIGN KEY (merchant_user_id, merchant_id)
    REFERENCES merchant_user(id, merchant_id),
  CONSTRAINT fk_mur_role FOREIGN KEY (merchant_role_id, merchant_id)
    REFERENCES merchant_role(id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stage 1: nullable ownership columns. Backfill before enforcing NOT NULL.
ALTER TABLE pms_product ADD COLUMN merchant_id BIGINT NULL,
  ADD KEY idx_pms_product_merchant_status (merchant_id, publish_status, id),
  ADD UNIQUE KEY uk_pms_product_scope (id, merchant_id);
ALTER TABLE pms_sku_stock ADD COLUMN merchant_id BIGINT NULL,
  ADD KEY idx_pms_sku_merchant_product (merchant_id, product_id, id);
ALTER TABLE oms_cart_item ADD COLUMN merchant_id BIGINT NULL,
  ADD KEY idx_oms_cart_member_merchant (member_id, merchant_id, delete_status, id);
ALTER TABLE oms_order_item ADD COLUMN merchant_id BIGINT NULL,
  ADD COLUMN merchant_order_id BIGINT NULL,
  ADD KEY idx_oms_item_merchant_order (merchant_id, merchant_order_id, id);
ALTER TABLE oms_order_return_apply ADD COLUMN merchant_id BIGINT NULL,
  ADD COLUMN merchant_order_id BIGINT NULL,
  ADD KEY idx_oms_return_merchant_order (merchant_id, merchant_order_id, id);

CREATE TABLE oms_merchant_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  merchant_order_sn VARCHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL,
  goods_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  freight_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  payable_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  delivery_company VARCHAR(64) NULL,
  delivery_sn VARCHAR(100) NULL,
  shipped_at DATETIME(6) NULL,
  received_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_merchant_order_sn (merchant_order_sn),
  UNIQUE KEY uk_merchant_order_scope (id, merchant_id),
  UNIQUE KEY uk_merchant_per_master (order_id, merchant_id),
  KEY idx_merchant_order_scope_status (merchant_id, status, created_at),
  CONSTRAINT fk_merchant_order_master FOREIGN KEY (order_id) REFERENCES oms_order(id),
  CONSTRAINT fk_merchant_order_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Metadata only. This table does not represent paid funds or settlement balances.
CREATE TABLE merchant_settlement_profile (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  settlement_subject_ref VARCHAR(128) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'UNCONFIGURED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_settlement_profile_merchant (merchant_id),
  CONSTRAINT fk_settlement_profile_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stage 2 backfill (reviewed migration script, not this draft) must:
-- 1. Insert exactly one merchant_code='PLATFORM_SELF'. Never assume its numeric id.
-- 2. Attribute all legacy products/SKUs/cart/items/returns to that merchant.
-- 3. Create one child order for each legacy oms_order and link its items/returns.
-- 4. Verify zero NULL ownerships, parent/child amount conservation and no orphan rows.
-- 5. Only then add NOT NULL and composite foreign keys in a separate reviewed migration.

-- Stage 3 proposed FK examples, NOT executable until backfill and source data cleanup:
-- ALTER TABLE pms_product MODIFY merchant_id BIGINT NOT NULL,
--   ADD CONSTRAINT fk_pms_product_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id);
-- ALTER TABLE pms_sku_stock MODIFY merchant_id BIGINT NOT NULL,
--   ADD CONSTRAINT fk_pms_sku_product_scope FOREIGN KEY (product_id,merchant_id)
--     REFERENCES pms_product(id,merchant_id);
-- ALTER TABLE oms_order_item MODIFY merchant_id BIGINT NOT NULL,
--   MODIFY merchant_order_id BIGINT NOT NULL,
--   ADD CONSTRAINT fk_oms_item_merchant_order FOREIGN KEY (merchant_order_id,merchant_id)
--     REFERENCES oms_merchant_order(id,merchant_id);
