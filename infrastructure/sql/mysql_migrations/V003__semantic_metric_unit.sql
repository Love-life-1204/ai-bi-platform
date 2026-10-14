-- 指标单位属于稳定指标身份，不随公式版本隐式改变。
ALTER TABLE `bi_metric`
    ADD COLUMN `metric_unit` VARCHAR(32) NOT NULL DEFAULT 'COUNT' AFTER `metric_name`;
