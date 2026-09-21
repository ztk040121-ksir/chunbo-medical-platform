-- 经典经方库种子数据
USE `chunbo_medical`;

INSERT INTO `tcm_formula` (`name`, `description`, `herbs_json`) VALUES
('小柴胡汤', '和解少阳，用于往来寒热、胸胁苦满、心烦喜呕', '[{"name":"柴胡","dose":12},{"name":"黄芩","dose":9},{"name":"党参","dose":9},{"name":"法半夏","dose":9},{"name":"炙甘草","dose":6},{"name":"生姜","dose":6},{"name":"大枣","dose":6}]'),
('补中益气汤', '补中益气，升阳举陷，用于脾胃虚弱、中气下陷', '[{"name":"黄芪","dose":15},{"name":"党参","dose":10},{"name":"炒白术","dose":10},{"name":"当归","dose":10},{"name":"陈皮","dose":6},{"name":"升麻","dose":6},{"name":"柴胡","dose":6},{"name":"炙甘草","dose":5}]'),
('四君子汤', '益气健脾，脾胃气虚之基础方', '[{"name":"党参","dose":10},{"name":"炒白术","dose":10},{"name":"茯苓","dose":10},{"name":"炙甘草","dose":6}]'),
('银翘散', '辛凉透表，清热解毒，用于风热感冒初期', '[{"name":"金银花","dose":12},{"name":"连翘","dose":12},{"name":"桔梗","dose":9},{"name":"薄荷","dose":6},{"name":"荆芥","dose":6},{"name":"牛蒡子","dose":9},{"name":"甘草","dose":5}]'),
('二陈汤', '燥湿化痰，理气和中，主治湿痰证', '[{"name":"法半夏","dose":10},{"name":"陈皮","dose":10},{"name":"茯苓","dose":12},{"name":"炙甘草","dose":6}]'),
('平胃散', '燥湿运脾，行气和胃，主治湿滞脾胃证', '[{"name":"苍术","dose":10},{"name":"厚朴","dose":9},{"name":"陈皮","dose":9},{"name":"炙甘草","dose":6}]');
