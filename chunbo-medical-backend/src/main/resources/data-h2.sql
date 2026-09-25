INSERT INTO patient (id, name, gender, age, phone, allergies, medical_history, remarks) VALUES
(1, '张建国', '男', 58, '13812345678', '青霉素过敏 (严重过敏性皮疹史)', '原发性高血压病史5年、高脂血症', '按时服药意识较差，需重点提醒用药规范'),
(2, '李秀英', '女', 45, '13987654321', '无药物过敏', '2型糖尿病3年、反流性食管炎', '近期空腹血糖波动在 8.5-9.2 mmol/L 之间'),
(3, '王小明', '男', 29, '13766668888', '磺胺类药物过敏', '慢性胃炎史半年', '平时经常加班熬夜，饮食作息不规律'),
(4, '陈素芬', '女', 66, '13599990000', '头孢类抗生素过敏', '高血压2级(极高危)、冠心病心绞痛', '长期服用降压药与阿司匹林');

INSERT INTO medicine (id, name, trade_name, specification, category, stock, price, unit, default_dosage, contraindications) VALUES
(1, '苯磺酸氨氯地平片', '络活喜', '5mg*7片/盒', 'CCB钙通道阻滞剂', 120, 28.50, '盒', '每次5mg，每日1次口服', '严重低血压、主动脉瓣狭窄患者禁用'),
(2, '缬沙坦胶囊', '代文', '80mg*7粒/盒', 'ARB降压药', 85, 36.80, '盒', '每次80mg，每日1次口服', '妊娠期妇女禁用、双侧肾动脉狭窄患者禁用'),
(3, '盐酸二甲双胍缓释片', '格华止', '0.5g*30片/盒', '降糖药(双胍类)', 200, 22.00, '盒', '每次0.5g，随餐服用，每日2次', '严重肾功能不全(eGFR<45)、急慢性代谢性酸中毒禁用'),
(4, '阿莫西林克拉维酸钾片', '安 augmentin', '0.375g*12片/盒', '青霉素类抗生素', 50, 42.00, '盒', '每次0.375g，每日3次口服', '青霉素皮试阳性及青霉素类药物过敏者严禁使用！'),
(5, '头孢克肟分散片', '世福素', '0.1g*6片/盒', '第三代头孢抗生素', 90, 31.50, '盒', '每次0.1g，每日2次口服', '对头孢类抗生素过敏者禁用'),
(6, '布洛芬缓释胶囊', '芬必得', '0.3g*20粒/盒', '解热镇痛抗炎(NSAIDs)', 160, 26.00, '盒', '每次0.3g，必要时每12小时1次', '活动性消化道溃疡、严重心功能衰竭、痛风慎用'),
(7, '阿托伐他汀钙片', '立普妥', '20mg*7片/盒', '他汀类降脂药', 75, 45.00, '盒', '每次20mg，每晚睡前口服1次', '活动性肝病或血清转氨酶持续升高者禁用'),
(8, '硝苯地平控释片', '拜新同', '30mg*7片/盒', 'CCB降压药', 15, 31.20, '盒', '每次30mg，每日1次清晨口服', '心源性休克、重度主动脉瓣狭窄禁用');

INSERT INTO clinic_guideline (id, disease_name, category, first_line_drugs, contraindications, clinical_pathway) VALUES
(1, '原发性高血压', '心血管慢病', 'CCB类(氨氯地平/硝苯地平控释片)、ARB类(缬沙坦/氯沙坦)', '妊娠期禁用ACEI/ARB类；心衰或心动过缓慎用特定β阻滞剂', '初诊评估危险分层 -> 一线长效单药或联合方案 -> 1-2周随访血压达标率'),
(2, '2型糖尿病', '内分泌代谢', '双胍类(二甲双胍)、SGLT-2抑制剂', '严重肾功能不全者二甲双胍需减量或停用', '生活方式干预同时启动二甲双胍 -> 定期复查糖化血红蛋白(HbA1c) -> 必要时联合用药'),
(3, '急性上呼吸道感染', '呼吸内科', '对症退热缓解药物、中成药；如明确细菌感染可选用敏感抗生素', '青霉素/头孢过敏者严禁使用对应β-内酰胺类抗生素', '鉴别病毒与细菌感染 -> 优先对症支持治疗 -> 严把抗生素使用指征');

INSERT INTO sys_ai_config (id, provider_name, base_url, api_key, model_name, temperature, max_tokens, is_active) VALUES
(1, 'OhMyGPT (在线真实大模型)', 'https://api.ohmygpt.com', 'sk-1FEAUBAdC6ee71Eaf9a3T3BLbkFJ6756Bd2A1B6B40B8aa77', 'gpt-4o-mini', 0.3, 2048, 1),
(2, '本地 Ollama (开源私有化部署)', 'http://localhost:11434', 'ollama', 'qwen2.5:7b', 0.5, 2048, 0),
(3, '阿里 DashScope (通义千问)', 'https://dashscope.aliyuncs.com/compatible-mode', 'sk-your-dashscope-key', 'qwen-plus', 0.3, 2048, 0),
(4, '春播万象内网离线 Mock', 'http://localhost:8080/mock', 'mock-key', 'chunbo-med-rule-engine', 0.1, 1024, 0);
INSERT INTO oa_salary_slip (id, doctor_id, doctor_name, salary_month, base_salary, clinic_commission, plaster_commission, deduction_social, tax, net_salary, status) VALUES
(1, 'DOC_1001', '李文华', '2026-08', 6500.00, 3200.00, 4850.00, 1150.00, 280.00, 13120.00, '已发放'),
(2, 'DOC_1001', '李文华', '2026-07', 6500.00, 2900.00, 4200.00, 1150.00, 240.00, 12210.00, '已发放'),
(3, 'DOC_1002', '王德全', '2026-08', 6000.00, 2800.00, 3100.00, 1100.00, 190.00, 10610.00, '已发放');

INSERT INTO oa_plaster_record (id, patient_name, plaster_type, paste_count, unit_price, total_amount, doctor_name, clinic_name, therapy_date) VALUES
(1, '张建国', '春播万象通络贴 (风湿骨痛)', 10, 48.00, 480.00, '李文华', '春播第001社区卫生服务站', '2026-08-05'),
(2, '李秀英', '小儿止咳化痰贴', 6, 38.00, 228.00, '李文华', '春播第001社区卫生服务站', '2026-08-12'),
(3, '王小明', '三伏冬病夏治温阳贴', 15, 52.00, 780.00, '李文华', '春播第001社区卫生服务站', '2026-08-18'),
(4, '陈素芬', '春播万象通络贴 (颈肩腰腿痛)', 12, 48.00, 576.00, '李文华', '春播第001社区卫生服务站', '2026-08-25'),
(5, '赵新民', '冬病夏治穴位贴', 8, 50.00, 400.00, '李文华', '春播第001社区卫生服务站', '2026-08-29');

INSERT INTO oa_approval (id, applicant_name, approval_type, reason, start_time, end_time, duration_days, status, approver_name, comment) VALUES
(1, '李文华', '调休申请', '上周周末参加基层全科慢病研讨会值班，申请周五调休', '2026-09-18 08:30', '2026-09-18 17:30', 1.0, '待人事初审', '张院长', '待院办复核'),
(2, '张小芳 (护士)', '事假申请', '家中有急事需返回原籍办理', '2026-09-10 08:30', '2026-09-11 17:30', 2.0, '已通过', '李文华', '已安排白班轮换顶岗');

INSERT INTO sys_token_log (id, session_id, model_name, prompt_tokens, completion_tokens, total_tokens, latency_ms, cost_cny) VALUES
(1, 'SESSION_1001', 'gpt-4o-mini', 850, 320, 1170, 1680, 0.00213),
(2, 'SESSION_1002', 'gpt-4o-mini', 620, 190, 810, 1240, 0.00138),
(3, 'SESSION_1003', 'gpt-4o-mini', 910, 410, 1320, 1850, 0.00255);