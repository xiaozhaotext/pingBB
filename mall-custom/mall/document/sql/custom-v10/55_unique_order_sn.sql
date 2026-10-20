-- New order numbers include oms_order.id, which is stable across Redis restarts.
-- Before applying this index to an existing database, inspect and repair old
-- duplicates with their oms_order_item references and any payment callbacks.
-- SELECT order_sn,COUNT(*),GROUP_CONCAT(id ORDER BY id)
-- FROM oms_order GROUP BY order_sn HAVING COUNT(*)>1;
ALTER TABLE oms_order ADD UNIQUE KEY uk_oms_order_sn (order_sn);

