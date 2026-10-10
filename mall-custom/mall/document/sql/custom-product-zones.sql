-- 增量迁移草案：只增加商品专区归属，不修改原表及原交易规则。
CREATE TABLE IF NOT EXISTS pms_product_zone (
  product_id BIGINT NOT NULL,
  zone_code VARCHAR(16) NOT NULL COMMENT 'PICK 自选区 / REBUY 复购区',
  sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (product_id, zone_code),
  KEY idx_zone_sort (zone_code, sort_order, product_id),
  CONSTRAINT fk_product_zone_product FOREIGN KEY (product_id) REFERENCES pms_product(id),
  CONSTRAINT chk_product_zone_code CHECK (zone_code IN ('PICK','REBUY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
