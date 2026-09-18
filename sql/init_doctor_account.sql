DROP TABLE IF EXISTS `sys_doctor_account`;
CREATE TABLE `sys_doctor_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号/工号',
  `password` VARCHAR(100) NOT NULL COMMENT '登录密码',
  `doctor_name` VARCHAR(50) NOT NULL COMMENT '医生真实姓名',
  `doctor_id` VARCHAR(50) NOT NULL COMMENT '数字工牌号(如 DOC_1001)',
  `department` VARCHAR(64) DEFAULT '全科门诊' COMMENT '所属科室',
  `title` VARCHAR(64) DEFAULT '主治医师' COMMENT '岗位职称',
  `qualification_no` VARCHAR(64) DEFAULT '110430105002819' COMMENT '医师资格/执业证号',
  `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
  `status` VARCHAR(20) DEFAULT 'ENABLE' COMMENT '状态 ENABLE/DISABLE',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生与医护人员登录账号表';

INSERT INTO `sys_doctor_account` (`username`, `password`, `doctor_name`, `doctor_id`, `department`, `title`, `phone`, `status`) VALUES
('kzt', '123456', '康主任', 'DOC_1001', '全科门诊 / 中医特色专科', '主任医师', '13800138001', 'ENABLE'),
('doc_1002', '123456', '张文浩', 'DOC_1002', '全科门诊 / 智慧药房', '主治医师 / 调剂药师', '13800138002', 'ENABLE'),
('doc_1003', '123456', '李文华', 'DOC_1003', '全科慢病门诊', '主任医师', '13800138003', 'ENABLE'),
('doc_1004', '123456', '赵敏', 'DOC_1004', '中医理疗特色门诊', '副主任医师', '13800138004', 'ENABLE');