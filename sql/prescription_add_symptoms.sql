-- 处方表加「问诊记录/主诉」字段（问诊记录闭环：医生开方时录入，手机端处方详情展示）
-- 已执行（2026-09-28）。
ALTER TABLE prescription ADD COLUMN symptoms TEXT NULL COMMENT '问诊记录/主诉(医生开方时录入)' AFTER diagnosis;
