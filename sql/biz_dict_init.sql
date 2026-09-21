-- 基础数据字典表：管理表单下拉可选项（规格单位/二级剂型/批准文号/生产厂家等）
CREATE TABLE IF NOT EXISTS `biz_dict` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(50) NOT NULL COMMENT '字典类型：规格单位/二级剂型/批准文号/生产厂家',
  `dict_value` VARCHAR(200) NOT NULL COMMENT '字典值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_value` (`dict_type`, `dict_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础数据字典(表单下拉可选项管理)';

-- 规格单位
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`) VALUES
('规格单位','盒'),('规格单位','瓶'),('规格单位','支'),('规格单位','袋'),('规格单位','贴'),
('规格单位','丸'),('规格单位','片'),('规格单位','粒'),('规格单位','包'),('规格单位','罐'),
('规格单位','套'),('规格单位','卷'),('规格单位','块'),('规格单位','副');

-- 二级剂型
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`) VALUES
('二级剂型','水丸'),('二级剂型','颗粒'),('二级剂型','胶囊'),('二级剂型','片剂'),('二级剂型','贴剂'),
('二级剂型','丸剂'),('二级剂型','注射剂'),('二级剂型','口服溶液剂'),('二级剂型','混悬滴剂'),
('二级剂型','配方颗粒'),('二级剂型','敷料'),('二级剂型','消毒液'),('二级剂型','管件'),('二级剂型','中医理疗');

-- 批准文号：从现有药品档案导入去重
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`)
SELECT '批准文号', approval_number FROM medicine WHERE approval_number IS NOT NULL AND approval_number <> ''
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value);

-- 生产厂家：从现有药品档案导入去重
INSERT IGNORE INTO `biz_dict` (`dict_type`, `dict_value`)
SELECT '生产厂家', manufacturer FROM medicine WHERE manufacturer IS NOT NULL AND manufacturer <> ''
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value);

SELECT dict_type, COUNT(*) cnt FROM biz_dict GROUP BY dict_type;
