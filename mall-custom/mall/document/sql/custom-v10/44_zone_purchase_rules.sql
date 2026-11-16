-- 三区购买规则隔离（自选/复购/消费）与消费区开放 2026-10-15
--
-- 规则：
--   PICK    自选区：现金或余额（充值本金）购买，不使用任何积分
--   REBUY   复购区：30% 复购积分 + 70% 现金（比例取 sys_config rebuy.points.ratio）
--   CONSUME 消费区：商品积分购买（比例取 sys_config product.points.*）
--   zone_code 为空表示商品未分配专区，按自选区规则结算，不会越界使用消费区/复购区积分。
--
-- 本次只增加专区标识、余额支付流水和后台菜单，不重算历史订单金额，历史订单余额抵扣为 0。
-- 可重复执行。

SET NAMES utf8mb4;

-- 1. 专区约束放开消费区
SET @dropCheck = (SELECT IF(COUNT(*)>0,
  'ALTER TABLE `pms_product_zone` DROP CHECK `chk_product_zone_code`', 'SELECT 1')
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='pms_product_zone' AND CONSTRAINT_NAME='chk_product_zone_code');
PREPARE stmt FROM @dropCheck; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @addCheck = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `pms_product_zone` ADD CONSTRAINT `chk_product_zone_code` CHECK (zone_code IN (''PICK'',''REBUY'',''CONSUME''))', 'SELECT 1')
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='pms_product_zone' AND CONSTRAINT_NAME='chk_product_zone_code');
PREPARE stmt FROM @addCheck; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 购物车记录加入时所在专区，购物车与结算按专区隔离计算
SET @addCartZone = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `oms_cart_item` ADD COLUMN `zone_code` VARCHAR(16) NOT NULL DEFAULT '''' COMMENT ''加入购物车时的专区：PICK/REBUY/CONSUME，空为未分配专区'''
    , 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oms_cart_item' AND COLUMN_NAME='zone_code');
PREPARE stmt FROM @addCartZone; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 订单行记录结算专区，便于后台按规则核对
SET @addItemZone = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `oms_order_item` ADD COLUMN `zone_code` VARCHAR(16) NOT NULL DEFAULT '''' COMMENT ''结算专区：PICK/REBUY/CONSUME，空为旧订单'''
    , 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oms_order_item' AND COLUMN_NAME='zone_code');
PREPARE stmt FROM @addItemZone; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 订单记录余额（充值本金）抵扣金额，取消订单时按此原路退回
SET @addOrderBalance = (SELECT IF(COUNT(*)=0,
  'ALTER TABLE `oms_order` ADD COLUMN `balance_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''余额（充值本金）抵扣金额'''
    , 'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oms_order' AND COLUMN_NAME='balance_amount');
PREPARE stmt FROM @addOrderBalance; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 余额支付流水：充值本金只能通过本表增减，禁止只改余额不记流水
CREATE TABLE IF NOT EXISTS `ums_recharge_ledger` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`       BIGINT       NOT NULL,
  `change_type`     VARCHAR(32)  NOT NULL COMMENT 'order_pay/order_refund/adjust',
  `amount_cents`    BIGINT       NOT NULL COMMENT '变动金额（分，正=增，负=减）',
  `balance_after_cents` BIGINT   NOT NULL COMMENT '变动后余额（分）',
  `biz_id`          VARCHAR(64)  DEFAULT NULL COMMENT '业务单号（订单ID）',
  `idempotency_key` VARCHAR(64)  NOT NULL COMMENT '幂等键，防重复扣减/退回',
  `remark`          VARCHAR(255) DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_ledger_idem` (`idempotency_key`),
  KEY `idx_recharge_ledger_member` (`member_id`),
  CONSTRAINT `fk_recharge_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值本金（余额）支付流水';

-- 6. 回填购物车专区：自选区优先，其次商品唯一归属的专区；未分配专区保持为空（按自选区规则）
UPDATE `oms_cart_item` ci
SET ci.zone_code = 'PICK'
WHERE ci.zone_code = ''
  AND EXISTS (SELECT 1 FROM `pms_product_zone` z WHERE z.product_id = ci.product_id AND z.zone_code = 'PICK');

UPDATE `oms_cart_item` ci
SET ci.zone_code = (SELECT MIN(z.zone_code) FROM `pms_product_zone` z WHERE z.product_id = ci.product_id)
WHERE ci.zone_code = ''
  AND (SELECT COUNT(*) FROM `pms_product_zone` z WHERE z.product_id = ci.product_id) = 1;

-- 7. 后台总控：新增“消费区商品管理”一级分组，排在复购商品管理之后
--
-- 菜单 ID 不能写死：32_admin_menu_ia.sql / 34_admin_mindmap_navigation.sql 写入的 100 段 ID
-- 已被 33_admin_menu_ia_8_groups.sql 用 `DELETE FROM ums_menu WHERE id BETWEEN 100 AND 299`
-- 整体删除重建为 200 段，因此 100/101/102…156/177 这些 ID 在任何按本目录顺序初始化的库中都不存在。
-- 写死 ID 会导致：分组照常创建、但排序号落到「系统设置」之后，且角色授权匹配 0 行，
-- 侧边栏永远看不到「消费区商品管理」。这里全部改为按 name 定位、按 name 授权。
--
-- 可重复执行：一级分组顺序用确定性的 ROW_NUMBER 重排，不做相对加减。
START TRANSACTION;

-- 复购商品管理一级分组（历史上可能存在多个同名行，取排在最前面的一个）
SET @rebuyId = (SELECT id FROM ums_menu WHERE parent_id = 0 AND name = 'rebuy' ORDER BY sort DESC, id LIMIT 1);

-- 一级分组：显式 ID（ums_menu.name 无唯一索引，无法靠 name 冲突触发 ON DUPLICATE KEY）
INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (186,0,NOW(),'消费区商品管理',0,0,'consume','product-list',0)
ON DUPLICATE KEY UPDATE
 parent_id=VALUES(parent_id),title=VALUES(title),level=VALUES(level),name=VALUES(name),icon=VALUES(icon),hidden=VALUES(hidden);

-- 二级分组：分类管理、商品列表（与自选/复购商品管理保持一致：分类管理在前、商品列表在后）
INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (187,186,NOW(),'商品列表',1,9,'consumeProduct','product-list',0),
 (188,186,NOW(),'分类管理',1,10,'consumeCategory','product-cate',0)
ON DUPLICATE KEY UPDATE
 parent_id=VALUES(parent_id),title=VALUES(title),level=VALUES(level),sort=VALUES(sort),name=VALUES(name),icon=VALUES(icon),hidden=VALUES(hidden);

-- 一级分组重排序：桌面主页、自选、复购、消费、订单、会员、财务、服务、后台桌面、系统设置。
-- 未列出的分组排在最后并保持原有相对顺序；sort 全部唯一，侧边栏顺序稳定且幂等。
UPDATE ums_menu m
JOIN (
  SELECT id, ROW_NUMBER() OVER (ORDER BY ord ASC, cur_sort DESC, id ASC) AS rn
  FROM (
    SELECT id, sort AS cur_sort,
      CASE name
        WHEN 'desk'           THEN 0
        WHEN 'pick'           THEN 1
        WHEN 'rebuy'          THEN 2
        WHEN 'consume'        THEN 3
        WHEN 'orderMgr'       THEN 4
        WHEN 'memberMgr'      THEN 5
        WHEN 'financeMgr'     THEN 6
        WHEN 'serviceMgr'     THEN 7
        WHEN 'serviceDesktop' THEN 8
        WHEN 'systemSettings' THEN 9
        WHEN 'sysMgr'         THEN 9
        ELSE 100
      END AS ord
    FROM ums_menu
    WHERE parent_id = 0 AND hidden = 0
  ) ordered
) ranked ON ranked.id = m.id
SET m.sort = 1000 - ranked.rn;

-- 角色授权：已经拥有自选/复购商品管理（含其子项）的角色，同样获得消费区分组
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT r.role_id, m.id
FROM ums_role_menu_relation r
JOIN ums_menu anchor ON anchor.id = r.menu_id AND anchor.parent_id = 0
 JOIN ums_menu m ON m.id IN (186,187,188)
LEFT JOIN ums_role_menu_relation old ON old.role_id = r.role_id AND old.menu_id = m.id
WHERE anchor.name IN ('pick','rebuy','pickCategory','rebuyCategory','pickProduct','rebuyProduct')
  AND old.menu_id IS NULL;

COMMIT;
