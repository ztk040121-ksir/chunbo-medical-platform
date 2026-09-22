-- 权限表升级：scope 区分两套系统（CLINIC 云诊所 5173 / ADMIN 管理中台 5174），角色×系统 各一行
ALTER TABLE sys_role_permission ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'CLINIC';
ALTER TABLE sys_role_permission DROP INDEX role, ADD UNIQUE KEY uk_role_scope (role, scope);

DELETE FROM sys_role_permission;

-- ===== 云诊所（5173）模块：registration 门诊挂号 / clinic 门诊接诊 / billing 划价收费
--      treatment 特色执行站 / pharmacy 智慧药房 / patient 患者档案 / ai-settings 设置 =====
INSERT INTO sys_role_permission (role, role_label, scope, modules_json, description) VALUES
('DOCTOR', '医生（DOCTOR）', 'CLINIC', '["registration","clinic","billing","treatment","pharmacy","patient","ai-settings"]', '门诊接诊、开单开方、预约挂号、处方发放、个人工商资料'),
('NURSE', '护士（NURSE）', 'CLINIC', '["registration","billing","treatment","pharmacy","patient"]', '门诊挂号、划价收费、特色执行、智慧药房、全部患者档案（不受医生归属限制）'),
('ADMIN', '系统最高管理员（ADMIN）', 'CLINIC', '["registration","clinic","billing","treatment","pharmacy","patient","ai-settings"]', '云诊所全部模块'),
('HR', '人事（HR）', 'CLINIC', '[]', '无云诊所权限（人事仅使用管理中台）'),
('MERCHANT', '商户（MERCHANT）', 'CLINIC', '[]', '无云诊所权限（商户仅使用管理中台）');

-- ===== 管理中台（5174）模块：analytics 经营分析大屏 / mall-orders 商城订单履约 /
--      mall-products 商城商品与进销存 / mall-users 商城注册用户 / salary 工资条 / approval OA审批 / doctors 医护账号与权限 =====
INSERT INTO sys_role_permission (role, role_label, scope, modules_json, description) VALUES
('DOCTOR', '医生（DOCTOR）', 'ADMIN', '["analytics","salary","approval"]', '经营分析大屏、我的工资条明细、我的OA请假申请'),
('NURSE', '护士（NURSE）', 'ADMIN', '["salary","approval"]', '我的工资条明细、我的OA请假申请'),
('HR', '人事（HR）', 'ADMIN', '["analytics","salary","approval","doctors"]', '经营分析大屏、工资条发放与核算、OA请假审批、医护账号与权限管理'),
('MERCHANT', '商户（MERCHANT）', 'ADMIN', '["mall-orders","mall-products","mall-users","salary"]', '商城订单履约发货、商品档案与进销存管理、商城注册用户、我的工资条明细'),
('ADMIN', '系统最高管理员（ADMIN）', 'ADMIN', '["analytics","mall-orders","mall-products","mall-users","salary","approval","doctors"]', '全系统最高管理权限、全院运营数据大屏、AI调度指挥中枢');

-- 商户真实账号（此前只有硬编码假数据，统一台账查不到）：merchant / 123456
INSERT INTO sys_staff_account (staff_id, username, password, real_name, department, title, phone, role, status)
VALUES ('MERCH_001', 'merchant', '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/K37dPxbW', '王商户', '春播商城运营部', '供应链主管', '13800000003', 'MERCHANT', 'ENABLE');

-- 21:48 修正：护士中台加经营大屏；商户中台=大屏/工资条/请假+商城三模块
UPDATE sys_role_permission SET modules_json='["analytics","salary","approval"]', description='经营分析大屏、我的工资条明细、我的OA请假申请' WHERE role='NURSE' AND scope='ADMIN';
UPDATE sys_role_permission SET modules_json='["analytics","salary","approval","mall-orders","mall-products","mall-users"]', description='经营分析大屏、我的工资条、请假申请，另含商城订单履约/商品进销存/注册用户管理' WHERE role='MERCHANT' AND scope='ADMIN';
