-- 复购智豆抵扣比例改为50%，只影响后续结算，不重算历史订单。
-- 周预算独立设置，初始为0；重复执行保留管理员已设置的周预算。
-- 旧日预算和历史日结批次保留用于审计，但不参与新的周结计算。
START TRANSACTION;
INSERT INTO sys_config(config_key,config_value,config_group,remark)
VALUES('rebuy.points.ratio','0.50','distribution','复购区智豆抵扣比例(0-1)')
ON DUPLICATE KEY UPDATE config_value='0.50';
INSERT INTO sys_config(config_key,config_value,config_group,remark) VALUES
('tri.pool.weekly.budget.partner','0.00','distribution','合伙人池每周发放上限，0暂停自动发放'),
('tri.pool.weekly.budget.cofounder','0.00','distribution','联创池每周发放上限，0暂停自动发放')
ON DUPLICATE KEY UPDATE config_key=VALUES(config_key);
COMMIT;
