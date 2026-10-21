-- 仅保存模型返回的计量字段，不保存提示词或工具原文；唯一事件+序号避免重复记账。
CREATE TABLE IF NOT EXISTS ai_model_usage (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_id VARCHAR(36) NOT NULL,
 call_index INT NOT NULL,
 member_id BIGINT UNSIGNED NOT NULL,
 provider VARCHAR(16) NOT NULL,
 model VARCHAR(150) NOT NULL,
 prompt_tokens BIGINT NOT NULL,
 completion_tokens BIGINT NOT NULL,
 estimated_cost DECIMAL(16,8),
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_ai_usage_event(event_id,call_index),
 KEY idx_ai_usage_member(member_id,created_at)
);
