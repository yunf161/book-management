package com.yunfeil161.ailibrary.common.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为业务写操作声明一把 Redisson 可重入分布式锁。
 *
 * <p>当前只接受集中定义的固定锁键。锁切面运行在事务切面外层，确保数据库事务
 * 已经提交或回滚后才释放锁，避免下一个请求获得锁却看不到前一事务的结果。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

  /** 兼容旧写法的单个锁键。 */
  String value() default "";

  /** 锁键列表，支持 Spring EL，例如 {@code 'book:' + #id}。 */
  String[] keys() default {};

  /** 查询使用读锁，修改使用写锁。 */
  LockMode mode() default LockMode.WRITE;
}
