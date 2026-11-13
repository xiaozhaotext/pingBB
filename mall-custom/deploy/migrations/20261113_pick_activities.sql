-- 旅游活动及正品关联独立存储，不改动其他专区分类及历史订单数据。
CREATE TABLE IF NOT EXISTS pms_pick_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    rules TEXT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    KEY idx_pick_activity_visible (enabled,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pms_pick_activity_product (
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (activity_id,product_id),
    KEY idx_pick_activity_product (product_id),
    CONSTRAINT fk_pick_activity FOREIGN KEY (activity_id) REFERENCES pms_pick_activity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 下线赠品菜单入口，保留原有赠品历史数据供审计。
UPDATE ums_menu SET hidden=1 WHERE name IN ('giftOptionsMgr','giftOptions');
-- 活动接口沿用商品分类权限资源，原分类权限不变。
