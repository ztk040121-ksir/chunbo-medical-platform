-- 订单表加下单用户外键 user_id，用于「我的订单」按用户精确隔离（替代 buyer_name 文本 like 匹配）
-- 已执行（2026-09-23）。历史订单 user_id 为空，查询时回退按 buyer_name 昵称/手机号匹配。
ALTER TABLE mall_order ADD COLUMN user_id BIGINT NULL COMMENT '下单用户主键 mall_user.id' AFTER buyer_name;

-- 历史订单回填（可选）：按 buyer_name 中「收货人 (手机号)」的手机号反查 mall_user 补 user_id
-- UPDATE mall_order o JOIN mall_user u ON o.buyer_name LIKE CONCAT('%(', u.phone, ')%') SET o.user_id = u.id WHERE o.user_id IS NULL;
