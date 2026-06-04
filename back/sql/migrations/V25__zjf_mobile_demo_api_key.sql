-- V3 M9: 找家纺移动下单演示 API Key（请求头 X-Mobile-Api-Key）

INSERT INTO `t_external_api_key` (
  `key_name`, `api_key`, `status`, `rate_limit_per_minute`, `total_calls`, `deleted`
)
SELECT
  '找家纺移动下单（演示）',
  'ZJF-MOBILE-DEMO-2026',
  'ACTIVE',
  120,
  0,
  0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `t_external_api_key` k
  WHERE k.api_key = 'ZJF-MOBILE-DEMO-2026' AND k.deleted = 0
);
