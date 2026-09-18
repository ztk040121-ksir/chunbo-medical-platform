-- ========================================================
-- 春播云诊所 数据库建表脚本与种子数据
-- 数据库: chunbo_medical
-- ========================================================

CREATE DATABASE IF NOT EXISTS `chunbo_medical` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `chunbo_medical`;

-- 1. 患者档案表
DROP TABLE IF EXISTS `patient`;
CREATE TABLE `patient` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '患者主键ID',
  `name` VARCHAR(50) NOT NULL COMMENT '姓名',
  `gender` VARCHAR(10) NOT NULL COMMENT '性别: 男/女',
  `age` INT NOT NULL COMMENT '年龄',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `allergies` VARCHAR(255) DEFAULT '无' COMMENT '过敏史(极其重要，AI开方强校验)',
  `medical_history` VARCHAR(500) DEFAULT '无' COMMENT '既往慢病史(高血压/糖尿病/冠心病等)',
  `remarks` VARCHAR(255) DEFAULT NULL COMMENT '特殊注意事项',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '建档时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者档案表';

-- 2. 药品库存与规范目录表
DROP TABLE IF EXISTS `medicine`;
CREATE TABLE `medicine` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '药品ID',
  `name` VARCHAR(100) NOT NULL COMMENT '通用名称',
  `trade_name` VARCHAR(100) DEFAULT NULL COMMENT '商品名',
  `specification` VARCHAR(50) NOT NULL COMMENT '规格(如 5mg*7片/盒)',
  `category` VARCHAR(50) NOT NULL COMMENT '药理分类(降压药/抗生素/解热镇痛等)',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '诊所药房当前可用库存',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价(元)',
  `unit` VARCHAR(20) NOT NULL DEFAULT '盒' COMMENT '包装单位',
  `default_dosage` VARCHAR(100) DEFAULT NULL COMMENT '常用用法用量参考',
  `contraindications` VARCHAR(255) DEFAULT NULL COMMENT '用药禁忌(AI辅助安全核对)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊所药房药品库存表';

-- 3. 处方主表
DROP TABLE IF EXISTS `prescription`;
CREATE TABLE `prescription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '处方ID',
  `prescription_no` VARCHAR(64) NOT NULL COMMENT '处方编号',
  `patient_id` BIGINT NOT NULL COMMENT '关联患者ID',
  `patient_name` VARCHAR(50) NOT NULL COMMENT '患者姓名',
  `doctor_name` VARCHAR(50) NOT NULL DEFAULT '主诊医师' COMMENT '接诊医生',
  `diagnosis` VARCHAR(255) NOT NULL COMMENT '临床诊断结论',
  `ai_advice` TEXT DEFAULT NULL COMMENT 'AI问诊生成的辅助诊疗建议与用药提示',
  `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '处方总金额',
  `status` INT NOT NULL DEFAULT 0 COMMENT '状态: 0-草稿待确认 1-医生已签发 2-已调配发药',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开具时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_presc_no` (`prescription_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方主表';

-- 4. 处方明细表(药品用药清单)
DROP TABLE IF EXISTS `prescription_item`;
CREATE TABLE `prescription_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `prescription_id` BIGINT NOT NULL COMMENT '处方主表ID',
  `medicine_id` BIGINT NOT NULL COMMENT '药品ID',
  `medicine_name` VARCHAR(100) NOT NULL COMMENT '药品名称',
  `specification` VARCHAR(50) NOT NULL COMMENT '规格',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `price` DECIMAL(10,2) NOT NULL COMMENT '单价',
  `dosage` VARCHAR(50) NOT NULL COMMENT '单次剂量(如 1片)',
  `frequency` VARCHAR(50) NOT NULL COMMENT '频次(如 一日一次/一日三次)',
  `route` VARCHAR(30) NOT NULL DEFAULT '口服' COMMENT '给药途径',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='处方明细表';

-- 5. 基层医学知识库(标准诊疗方案与禁忌)
DROP TABLE IF EXISTS `clinical_case`;
CREATE TABLE `clinical_case` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '病案指南ID',
  `disease_name` VARCHAR(100) NOT NULL COMMENT '疾病名称',
  `typical_symptoms` VARCHAR(500) NOT NULL COMMENT '典型症状特征',
  `diagnosis_standard` TEXT NOT NULL COMMENT '基层诊断标准',
  `recommended_treatment` TEXT NOT NULL COMMENT '规范用药方案',
  `caution_warnings` TEXT DEFAULT NULL COMMENT '用药禁忌与转诊指征',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基层临床指南与病案表';

-- ========================================================
-- 种子数据初始化
-- ========================================================

-- 患者测试数据
INSERT INTO `patient` (`id`, `name`, `gender`, `age`, `phone`, `allergies`, `medical_history`, `remarks`) VALUES
(1, '张建国', '男', 58, '13812345678', '青霉素过敏 (严重过敏性皮疹史)', '原发性高血压病史5年、高脂血症', '按时服药意识较差，需重点提醒用药规范'),
(2, '李秀英', '女', 45, '13987654321', '无药物过敏', '2型糖尿病3年、反流性食管炎', '近期空腹血糖波动在 8.5-9.2 mmol/L 之间'),
(3, '王小明', '男', 29, '13766668888', '磺胺类药物过敏', '无慢性病史', '偶有痛风发作，肝肾功能正常'),
(4, '陈素芬', '女', 66, '13599990000', '头孢类抗生素过敏', '高血压2级(极高危)、冠心病心绞痛', '长期服用降压药与阿司匹林');

-- 药房库存数据
INSERT INTO `medicine` (`id`, `name`, `trade_name`, `specification`, `category`, `stock`, `price`, `unit`, `default_dosage`, `contraindications`) VALUES
(1, '苯磺酸氨氯地平片', '络活喜', '5mg*7片/盒', 'CCB钙通道阻滞剂', 120, 28.50, '盒', '每次5mg，每日1次口服', '严重低血压、主动脉瓣狭窄患者禁用'),
(2, '缬沙坦胶囊', '代文', '80mg*7粒/盒', 'ARB降压药', 85, 36.80, '盒', '每次80mg，每日1次口服', '妊娠期妇女禁用、双侧肾动脉狭窄患者禁用'),
(3, '盐酸二甲双胍缓释片', '格华止', '0.5g*30片/盒', '降糖药(双胍类)', 200, 22.00, '盒', '每次0.5g，随餐服用，每日2次', '严重肾功能不全(eGFR<45)、急慢性代谢性酸中毒禁用'),
(4, '阿莫西林克拉维酸钾片', '安 augmentin', '0.375g*12片/盒', '青霉素类抗生素', 50, 42.00, '盒', '每次0.375g，每日3次口服', '青霉素皮试阳性及青霉素类药物过敏者严禁使用！'),
(5, '头孢克肟分散片', '世福素', '0.1g*6片/盒', '第三代头孢抗生素', 90, 31.50, '盒', '每次0.1g，每日2次口服', '对头孢类抗生素过敏者禁用'),
(6, '布洛芬缓释胶囊', '芬必得', '0.3g*20粒/盒', '解热镇痛抗炎(NSAIDs)', 160, 26.00, '盒', '每次0.3g，必要时每12小时1次', '活动性消化性溃疡、严重心功能衰竭、痛风慎用'),
(7, '阿托伐他汀钙片', '立普妥', '20mg*7片/盒', '他汀类降脂药', 75, 45.00, '盒', '每次20mg，每晚睡前口服1次', '活动性肝病或血清转氨酶持续升高者禁用');

-- 基层临床病案指南
INSERT INTO `clinical_case` (`disease_name`, `typical_symptoms`, `diagnosis_standard`, `recommended_treatment`, `caution_warnings`) VALUES
('原发性高血压(1级/2级)', '头晕、头胀、颈项板紧、疲劳、心悸，诊室测血压>=140/90mmHg', '非同日3次测得收缩压>=140mmHg和(或)舒张压>=90mmHg', '首选长效单药或联合降压方案：CCB类(如氨氯地平 5mg qd) 或 ARB类(如缬沙坦 80mg qd)；若血压>=160/100mmHg建议CCB+ARB联合用药。', '注意查问患者有无踝部水肿(CCB常见副反应)或高血钾(ARB注意监测肾功)；忌剧烈快速降压。'),
('2型糖尿病(初期/稳定期)', '多饮、多食、多尿、体重减轻(三多一少)，空腹血糖>=7.0mmol/L', '典型糖尿病症状+随机血糖>=11.1mmol/L，或空腹血糖>=7.0mmol/L', '一线首选双胍类药物：盐酸二甲双胍缓释片，初始剂量0.5g bid，餐中服用以减轻胃肠道反应；配合饮食与运动干预。', '注意筛查肝肾功能，eGFR<45ml/min应减量或停用；警惕低血糖反应，指导随身携带糖块。'),
('急性上呼吸道感染(伴咽痛/低热)', '咽干、咽痛、鼻塞、流涕、低热、全身乏力、轻度咳嗽', '病毒或细菌感染引起的鼻腔、咽喉部急性炎症，查体咽部充血', '对症支持为主：体温>38.5C或头痛明显者选用布洛芬缓释胶囊；若伴脓性分泌物或白细胞升高高度怀疑细菌感染，首选敏感口服抗生素(严格排查青霉素过敏史)。', '开具抗生素前必须强行校验过敏史！若有青霉素过敏史严禁开具阿莫西林；注意休息，多饮温水。');

-- 历史处方样例
INSERT INTO `prescription` (`id`, `prescription_no`,`patient_id`, `patient_name`, `doctor_name`, `diagnosis`, `ai_advice`, `total_amount`, `status`, `create_time`) VALUES
(1, 'RX20250510001', 1, '张建国', '李医生', '原发性高血压1级', '建议长期服用长效二氢吡啶类CCB，监测清晨及夜间血压，清淡少盐饮食。', 28.50, 2, '2025-05-10 09:30:00');

INSERT INTO `prescription_item` (`id`, `prescription_id`, `medicine_id`, `medicine_name`, `specification`, `quantity`, `price`, `dosage`, `frequency`, `route`) VALUES
(1, 1, 1, '苯磺酸氨氯地平片', '5mg*7片/盒', 1, 28.50, '5mg(1片)', '每日1次(早晨)', '口服');

-- ========================================================
-- 迁移 2026-09-18: 商城商品档案支持图片 (mall_product.image_url)
-- 图片文件存放于后端 uploads/products/ 目录, 通过 /uploads/** 静态访问
-- ========================================================
ALTER TABLE `mall_product` ADD COLUMN `image_url` VARCHAR(255) DEFAULT NULL COMMENT '商品图片URL' AFTER `cs_pitch`;

-- ========================================================
-- 迁移 2026-09-18: 挂号时采集患者详细资料 (选填)
-- ========================================================
ALTER TABLE `clinic_registration`
  ADD COLUMN `marriage` VARCHAR(20) DEFAULT NULL COMMENT '婚姻状况',
  ADD COLUMN `height` VARCHAR(20) DEFAULT NULL COMMENT '身高',
  ADD COLUMN `weight` VARCHAR(20) DEFAULT NULL COMMENT '体重',
  ADD COLUMN `job` VARCHAR(50) DEFAULT NULL COMMENT '职业',
  ADD COLUMN `company` VARCHAR(100) DEFAULT NULL COMMENT '工作单位',
  ADD COLUMN `wechat` VARCHAR(50) DEFAULT NULL COMMENT '微信号',
  ADD COLUMN `insurance_no` VARCHAR(50) DEFAULT NULL COMMENT '医保号',
  ADD COLUMN `accompany` VARCHAR(50) DEFAULT NULL COMMENT '陪护人',
  ADD COLUMN `accompany_phone` VARCHAR(20) DEFAULT NULL COMMENT '陪护人电话';
