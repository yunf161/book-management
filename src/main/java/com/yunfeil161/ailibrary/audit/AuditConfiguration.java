package com.yunfeil161.ailibrary.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 固定嵌套顺序：资源锁 → 业务事务（含REQUIRES_NEW）→ 审计快照与业务执行。 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE + 50)
public class AuditConfiguration {}
