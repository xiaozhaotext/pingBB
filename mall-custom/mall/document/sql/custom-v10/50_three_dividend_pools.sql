-- Additive schema only. Does not enable payments, migrate identities or recalculate historical balances.
CREATE TABLE IF NOT EXISTS tri_pool (
 code VARCHAR(16) PRIMARY KEY,
 balance DECIMAL(18,2) NOT NULL DEFAULT 0,
 total_in DECIMAL(18,2) NOT NULL DEFAULT 0,
 total_out DECIMAL(18,2) NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CHECK (balance>=0 AND total_in>=0 AND total_out>=0)
) ENGINE=InnoDB;
INSERT IGNORE INTO tri_pool(code) VALUES ('PARTNER'),('COFOUNDER'),('OPERATIONS');

CREATE TABLE IF NOT EXISTS tri_member (
 member_id BIGINT PRIMARY KEY,
 tier VARCHAR(16) NOT NULL,
 direct_paid_count BIGINT NOT NULL DEFAULT 0,
 joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CHECK (tier IN ('MEMBER','PARTNER','COFOUNDER') AND direct_paid_count>=0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_membership_product (
 product_id BIGINT PRIMARY KEY,
 enabled TINYINT NOT NULL DEFAULT 1,
 updated_by BIGINT NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_order_snapshot (
 order_id BIGINT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 referrer_id BIGINT NULL,
 price DECIMAL(18,2) NOT NULL,
 rule_version VARCHAR(32) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_tri_snapshot_member(member_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_payment_event (
 source_key VARCHAR(100) PRIMARY KEY,
 member_id BIGINT NOT NULL UNIQUE,
 referrer_id BIGINT NULL,
 paid_amount DECIMAL(18,2) NOT NULL,
 rule_version VARCHAR(32) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_operations_member (
 member_id BIGINT PRIMARY KEY,
 shares BIGINT NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 updated_by BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CHECK (shares>0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_pool_batch (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 pool_code VARCHAR(16) NOT NULL,
 source_key VARCHAR(100) NOT NULL,
 balance_before DECIMAL(18,2) NOT NULL,
 balance_after DECIMAL(18,2) NOT NULL,
 total_shares BIGINT NOT NULL,
 paid_total DECIMAL(18,2) NOT NULL,
 operator_id BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_tri_batch(pool_code,source_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_pool_distribution (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 batch_id BIGINT NOT NULL,
 pool_code VARCHAR(16) NOT NULL,
 member_id BIGINT NOT NULL,
 identity_snapshot VARCHAR(16) NOT NULL,
 shares BIGINT NOT NULL,
 total_shares BIGINT NOT NULL,
 cap_amount DECIMAL(18,2) NULL,
 received_before DECIMAL(18,2) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_tri_award(batch_id,member_id),
 KEY ix_tri_member_pool(member_id,pool_code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_pool_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 pool_code VARCHAR(16) NOT NULL,
 source_key VARCHAR(100) NOT NULL,
 entry_type VARCHAR(16) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,
 balance_after DECIMAL(18,2) NOT NULL,
 operator_id BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_tri_pool_entry(pool_code,source_key,entry_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tri_pool_position (
 member_id BIGINT NOT NULL,
 pool_code VARCHAR(16) NOT NULL,
 received DECIMAL(18,2) NOT NULL DEFAULT 0,
 PRIMARY KEY(member_id,pool_code),
 CHECK (received>=0)
) ENGINE=InnoDB;
-- Only bootstrap missing rollups; never overwrite existing balances when rerunning the migration.
INSERT IGNORE INTO tri_pool_position(member_id,pool_code,received)
 SELECT member_id,pool_code,SUM(amount) FROM tri_pool_distribution GROUP BY member_id,pool_code;

-- Menu/resource additions only; retain existing role authorizations and old ledgers.
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
 SELECT p.id,NOW(),n.title,1,n.sort,n.name,'total-yesterday',0
 FROM ums_menu p JOIN (
 SELECT 'financePartnerPool' AS name,'合伙人分红池' AS title,70 AS sort
 UNION ALL SELECT 'financeCofounderPool','联创分红池',69
 UNION ALL SELECT 'financeOperationsPool','运营分红池',68) n
 WHERE p.name='financeMgr' AND NOT EXISTS (SELECT 1 FROM ums_menu m WHERE m.name=n.name);
INSERT INTO ums_role_menu_relation(role_id,menu_id)
 SELECT DISTINCT r.role_id,n.id FROM ums_role_menu_relation r
 JOIN ums_menu p ON p.id=r.menu_id AND p.name='financeMgr'
 JOIN ums_menu n ON n.name IN ('financePartnerPool','financeCofounderPool','financeOperationsPool')
 WHERE NOT EXISTS (SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=r.role_id AND x.menu_id=n.id);
INSERT INTO ums_resource(create_time,name,url,description,category_id)
 SELECT NOW(),'三分红池管理','/p0/tri-pools/**','合伙人、联创、运营分红池',NULL
 WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/p0/tri-pools/**');
INSERT INTO ums_role_resource_relation(role_id,resource_id)
 SELECT DISTINCT r.role_id,n.id FROM ums_role_resource_relation r
 JOIN ums_resource o ON o.id=r.resource_id
 JOIN ums_resource n ON n.url='/p0/tri-pools/**'
 WHERE o.url='/p0/**' AND NOT EXISTS
 (SELECT 1 FROM ums_role_resource_relation x WHERE x.role_id=r.role_id AND x.resource_id=n.id);
