-- V31: 恢复厂内示意站供「园区内部」移动下单；真实地图仍用 ZJF 站

UPDATE `t_station`
SET `status` = 'ACTIVE',
    `remark` = CONCAT(IFNULL(NULLIF(`remark`, ''), '厂内示意站'), ' · V31 恢复移动下单')
WHERE `deleted` = 0
  AND `station_code` REGEXP '^(A[1-4]|B[1-4])$'
  AND (`area` IS NULL OR `area` NOT IN ('ZJF'));
