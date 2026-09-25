-- 处方表加签字字段（处方签字闭环：开方医生提交即签字确认，签字人/签字时间真实落库）
-- 已执行（2026-09-23）。medical 红线：处方必须带签字人才可追溯。
ALTER TABLE prescription ADD COLUMN signed_by VARCHAR(64) NULL COMMENT '处方签字医生' AFTER ai_advice;
ALTER TABLE prescription ADD COLUMN signed_at DATETIME NULL COMMENT '签字时间' AFTER signed_by;
