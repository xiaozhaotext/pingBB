-- 仅用于独立 mall_phase3a_merchant_test 测试库；执行前须先导入 Phase3A 扩展 DDL。
-- 历史订单只有官方单商户归属，按每个主单一个自营子单回填；不做资金结算。
START TRANSACTION;

INSERT INTO merchant(merchant_code,merchant_name,merchant_type,status,review_status)
VALUES ('PLATFORM_SELF','平台自营（历史测试数据）','PLATFORM_SELF','ACTIVE','APPROVED')
ON DUPLICATE KEY UPDATE merchant_code=VALUES(merchant_code);
SET @self_merchant_id := (SELECT id FROM merchant WHERE merchant_code='PLATFORM_SELF');

UPDATE pms_product SET merchant_id=@self_merchant_id WHERE merchant_id IS NULL;
UPDATE pms_sku_stock SET merchant_id=@self_merchant_id WHERE merchant_id IS NULL;
UPDATE oms_cart_item SET merchant_id=@self_merchant_id WHERE merchant_id IS NULL;

INSERT INTO oms_merchant_order
  (order_id,merchant_id,merchant_order_sn,status,goods_amount,freight_amount,discount_amount,payable_amount,created_at)
SELECT o.id,@self_merchant_id,CONCAT(o.order_sn,'-SELF'),
  CASE o.status WHEN 0 THEN 'PENDING_PAYMENT' WHEN 1 THEN 'PAID'
    WHEN 2 THEN 'SHIPPED' WHEN 3 THEN 'COMPLETED' WHEN 4 THEN 'CLOSED' ELSE 'UNKNOWN' END,
  COALESCE(o.total_amount,0),COALESCE(o.freight_amount,0),
  GREATEST(0,COALESCE(o.total_amount,0)+COALESCE(o.freight_amount,0)-COALESCE(o.pay_amount,0)),
  COALESCE(o.pay_amount,0),COALESCE(o.create_time,NOW())
FROM oms_order o
ON DUPLICATE KEY UPDATE order_id=VALUES(order_id);

UPDATE oms_order_item i JOIN oms_merchant_order mo ON mo.order_id=i.order_id
SET i.merchant_id=mo.merchant_id,i.merchant_order_id=mo.id
WHERE i.merchant_id IS NULL OR i.merchant_order_id IS NULL;

UPDATE oms_order_return_apply r JOIN oms_merchant_order mo ON mo.order_id=r.order_id
SET r.merchant_id=mo.merchant_id,r.merchant_order_id=mo.id
WHERE r.merchant_id IS NULL OR r.merchant_order_id IS NULL;

INSERT INTO merchant_settlement_profile(merchant_id,status)
VALUES (@self_merchant_id,'UNCONFIGURED')
ON DUPLICATE KEY UPDATE merchant_id=VALUES(merchant_id);

COMMIT;
