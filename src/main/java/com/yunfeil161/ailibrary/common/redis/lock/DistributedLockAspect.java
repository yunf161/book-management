package com.yunfeil161.ailibrary.common.redis.lock;

import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;

/**
 * 在数据库事务外层取得资源级 Redisson 读写锁。
 *
 * <p>多资源操作会先稳定排序，再逐把获取；释放时采用相反顺序。相同资源类型的
 * 键按字符串中的固定宽度 ID 排序，因此业务层构造数字键时应使用普通十进制 ID。</p>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DistributedLockAspect {

  private static final ExpressionParser SPEL = new SpelExpressionParser();
  private static final DefaultParameterNameDiscoverer PARAMETER_NAMES =
    new DefaultParameterNameDiscoverer();

  private final RedissonClient redissonClient;
  private final ApplicationContext applicationContext;

  @Value("${library.distributed-lock.wait-millis:3000}")
  private long waitMillis;

  @Around("@annotation(com.yunfeil161.ailibrary.common.redis.lock.DistributedLock)")
  public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = resolveMethod(joinPoint);
    DistributedLock declaration = AnnotatedElementUtils.findMergedAnnotation(
      method,
      DistributedLock.class
    );
    if (declaration == null) throw new IllegalStateException("未找到分布式锁配置");

    List<String> keys = resolveKeys(joinPoint, method, declaration);
    List<LockHandle> acquired = new ArrayList<>();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);

    try {
      for (String key : keys) {
        try {
          RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(key);
          RLock lock = declaration.mode() == LockMode.READ
            ? readWriteLock.readLock()
            : readWriteLock.writeLock();
          long remaining = Math.max(0L, deadline - System.nanoTime());
          if (!lock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
            throw new ConflictException("相同资源正在处理中，请稍后重试");
          }
          acquired.add(new LockHandle(key, lock));
        } catch (RedisException exception) {
          throw new ServiceUnavailableException(
            "并发控制服务暂时不可用，请稍后重试",
            exception
          );
        }
      }
      /*
       * 业务代码在锁获取异常处理之外执行。这样业务自身抛出的RedisException
       * 不会被误报为“获取业务锁失败”，全局异常处理可以保留真实故障含义。
       */
      return joinPoint.proceed();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ConflictException("请求等待业务锁时被中断，请重试");
    } finally {
      for (int index = acquired.size() - 1; index >= 0; index--) {
        LockHandle handle = acquired.get(index);
        try {
          if (handle.lock().isHeldByCurrentThread()) handle.lock().unlock();
        } catch (RedisException exception) {
          // 业务事务可能已经提交，不能用解锁故障覆盖真实业务结果。
          log.error(
            "释放分布式锁失败：key={}, mode={}, method={}",
            handle.key(),
            declaration.mode(),
            method.toGenericString(),
            exception
          );
        }
      }
    }
  }

  private List<String> resolveKeys(
    ProceedingJoinPoint joinPoint,
    Method method,
    DistributedLock declaration
  ) {
    List<String> declarations = new ArrayList<>();
    if (!declaration.value().isBlank()) declarations.add(declaration.value());
    declarations.addAll(Arrays.asList(declaration.keys()));
    if (declarations.isEmpty()) throw new IllegalStateException("分布式锁键不能为空");

    MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
      joinPoint.getTarget(), method, joinPoint.getArgs(), PARAMETER_NAMES
    );
    context.setBeanResolver(new BeanFactoryResolver(applicationContext));
    var session = LoginUserContext.getNullable();
    context.setVariable("currentMemberId", session == null ? "anonymous" : session.getMemberId());
    return declarations.stream()
      .map(expression -> expression.contains("#") || expression.contains("@")
        ? SPEL.parseExpression(expression).getValue(context)
        : expression)
      .flatMap(value -> {
        if (value instanceof String[] values) return Arrays.stream(values);
        if (value instanceof Iterable<?> values) {
          List<String> result = new ArrayList<>();
          values.forEach(item -> result.add(String.valueOf(item)));
          return result.stream();
        }
        return java.util.stream.Stream.of(String.valueOf(value));
      })
      .filter(key -> key != null && !key.isBlank())
      .distinct()
      .sorted(Comparator.comparingInt(this::resourceRank).thenComparing(String::compareTo))
      .toList();
  }

  private int resourceRank(String key) {
    String[] order = {
      ":policy-current:", ":member:", ":department:", ":role:", ":permission:",
      ":book-category:", ":book:", ":book-copy:",
      ":borrow-application:", ":borrow-record:", ":reading-plan:",
      ":reading-plan-item:", ":member-change-application:"
    };
    for (int index = 0; index < order.length; index++) {
      if (key.contains(order[index])) return index;
    }
    return order.length;
  }

  private Method resolveMethod(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    return AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
  }

  private record LockHandle(String key, RLock lock) {}
}
