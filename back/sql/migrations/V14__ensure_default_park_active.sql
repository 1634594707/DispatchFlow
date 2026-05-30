-- Ensure default demo park stays active for layout / digital twin APIs.
USE `fsd_core`;

UPDATE `t_park`
SET `status` = 'ACTIVE', `default_flag` = 1, `deleted` = 0
WHERE `park_code` = 'DEFAULT';

UPDATE `t_park`
SET `default_flag` = 0
WHERE `park_code` <> 'DEFAULT' AND `default_flag` = 1;
