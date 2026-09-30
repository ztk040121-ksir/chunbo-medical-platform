-- 挂号单支付方式落库 + 退号回补体验金（挂号缴费闭环补齐）
-- 待执行：执行前请先停止后端，执行后重启后端。
-- pay_method：微信支付 / 支付宝 / 到院支付 / 体验金抵扣
-- pay_username：体验金抵扣时的会员账号，退号时据此等额回补余额
ALTER TABLE clinic_registration ADD COLUMN pay_method VARCHAR(32) DEFAULT '' COMMENT '支付方式(微信支付/支付宝/到院支付/体验金抵扣)' AFTER pre_consultation_data;
ALTER TABLE clinic_registration ADD COLUMN pay_username VARCHAR(64) DEFAULT NULL COMMENT '体验金抵扣时的会员账号(退号回补)' AFTER pay_method;
