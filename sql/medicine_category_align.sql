-- 药品分类与医生开方分类一一对应迁移
USE `chunbo_medical`;

-- 1. 贴敷类药品归入「特色贴敷」分类（对应医生开方的特色贴敷处方区）
UPDATE `medicine` SET `primary_category`='特色贴敷', `secondary_category`='贴剂' WHERE `id`=18 AND `name` LIKE '%贴%';

-- 2. 医生开方「诊疗理疗项目」区的收费项目入库（此前开方页写死，现入库为可开单项目）
INSERT INTO `medicine` (`name`, `primary_category`, `secondary_category`, `specification`, `category`, `stock`, `price`, `unit`, `warning_stock`, `is_active`) VALUES
('按摩 (局部疏通理气)', '诊疗理疗项目', '中医理疗', '单次/30分钟', '中医理疗', 9999, 35.00, '次', 0, 1),
('艾灸温经通络', '诊疗理疗项目', '中医理疗', '单次/神阙中脘', '中医理疗', 9999, 40.00, '次', 0, 1),
('头部穴位推拿', '诊疗理疗项目', '中医理疗', '单次/20分钟', '中医理疗', 9999, 50.00, '次', 0, 1),
('腰椎牵引理疗', '诊疗理疗项目', '中医理疗', '单次/30分钟', '中医理疗', 9999, 45.00, '次', 0, 1);

SELECT primary_category, COUNT(*) cnt FROM medicine GROUP BY primary_category;
