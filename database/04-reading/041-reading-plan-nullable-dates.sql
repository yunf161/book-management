-- 部分早期本地库仍把日期设为NOT NULL。与040脚本、当前Service和H2保持一致：
-- 草稿计划允许尚未确定开始/结束日期，不改变已有日期值。
ALTER TABLE reading_plan
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;
