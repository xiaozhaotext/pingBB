-- ============================================================
-- 模块H：商城体验补齐（在线咨询/分享/视频等）
-- 文件：09_mall_experience.sql
-- 说明：商品视频建议作为 pms_product 的 video_url 字段扩展(另附 ALTER 脚本)。
--       此处实现在线咨询留言与分享记录。底部导航与入口整合由前端完成。
-- ============================================================

-- 在线咨询留言
DROP TABLE IF EXISTS `biz_consult_message`;
CREATE TABLE `biz_consult_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`   BIGINT       DEFAULT NULL,
  `nickname`    VARCHAR(64)  DEFAULT NULL,
  `content`     VARCHAR(512) NOT NULL COMMENT '留言内容',
  `reply`       VARCHAR(512) DEFAULT NULL COMMENT '客服回复',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0->未回复; 1->已回复',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线咨询留言表';

-- 分享记录
DROP TABLE IF EXISTS `biz_share_log`;
CREATE TABLE `biz_share_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`   BIGINT       DEFAULT NULL,
  `biz_type`    VARCHAR(32)  DEFAULT NULL COMMENT 'product/article/invitation',
  `biz_id`      VARCHAR(64)  DEFAULT NULL,
  `channel`     VARCHAR(32)  DEFAULT NULL COMMENT 'wechat/moments/qq',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享记录表';

-- 商品视频字段扩展（可选，独立执行，便于回滚）
-- ALTER TABLE `pms_product` ADD COLUMN `video_url` VARCHAR(512) DEFAULT NULL COMMENT '商品详情视频' AFTER `pic`;
