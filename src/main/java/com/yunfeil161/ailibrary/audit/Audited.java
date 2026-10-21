package com.yunfeil161.ailibrary.audit;

import java.lang.annotation.*;

/** 明确标记业务写入口；表达式只定义资源ID，不序列化请求参数。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
  AuditResource resource();

  String id() default "";

  /** 无数据库字段变化但有安全语义的操作，如拒绝须知并退出会话。 */
  boolean always() default false;
}
