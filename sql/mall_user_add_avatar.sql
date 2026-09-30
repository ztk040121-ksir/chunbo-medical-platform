-- 为商城用户表和患者表增加头像字段
ALTER TABLE mall_user ADD COLUMN avatar VARCHAR(500) NULL COMMENT '用户头像URL或预设标示' AFTER nickname;
ALTER TABLE patient ADD COLUMN avatar VARCHAR(500) NULL COMMENT '患者头像URL或预设标示' AFTER name;

-- 为既有用户赋予默认清新居民头像
UPDATE mall_user SET avatar = 'avatar_resident_1' WHERE avatar IS NULL OR avatar = '';
UPDATE patient SET avatar = 'avatar_resident_1' WHERE avatar IS NULL OR avatar = '';
