-- 规格字典迁移：规格单位(简单单位) → 规格模板(完整包装规格，选中后可自由修改数字)
DELETE FROM `biz_dict` WHERE `dict_type` = '规格单位';

INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`)
SELECT '规格模板', specification FROM medicine
WHERE specification IS NOT NULL AND specification <> '' AND specification <> '标准'
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value);

-- 常见规格模板补充
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`) VALUES
('规格模板','5mg*7片/盒'),('规格模板','0.25g*24粒/盒'),('规格模板','10ml*10支/盒'),
('规格模板','15ml/瓶'),('规格模板','2ml*5支/盒'),('规格模板','10g*6袋/盒'),
('规格模板','0.5g*30片/盒'),('规格模板','1g(相当于饮片5g)/袋'),('规格模板','8贴/盒'),
('规格模板','12g*10袋/盒'),('规格模板','0.1g*6袋/盒'),('规格模板','0.3g*20粒/盒');

-- 库位码字典：从现有档案导入 + 常用货架位
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`)
SELECT DISTINCT '库位码', location_code FROM medicine
WHERE location_code IS NOT NULL AND location_code <> ''
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value);

INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`) VALUES
('库位码','A-01-01'),('库位码','A-01-02'),('库位码','A-02-01'),('库位码','B-01-01'),
('库位码','B-02-01'),('库位码','C-01-01'),('库位码','C-02-01'),('库位码','D-01-01'),
('库位码','D-01-03'),('库位码','E-01-01'),('库位码','E-02-01');

SELECT dict_type, COUNT(*) cnt FROM biz_dict GROUP BY dict_type;
