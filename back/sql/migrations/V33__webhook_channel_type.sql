USE `fsd_core`;

ALTER TABLE `t_webhook_subscription`
    ADD COLUMN `channel_type` VARCHAR(32) NOT NULL DEFAULT 'GENERIC' COMMENT '渠道类型: GENERIC/WECHAT_BOT/DINGTALK_BOT/FEISHU_BOT' AFTER `callback_url`;