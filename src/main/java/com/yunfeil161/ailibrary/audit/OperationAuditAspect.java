package com.yunfeil161.ailibrary.audit;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;

/** 锁在最外层，审计事务包住业务事务；成功日志不允许提交后补写。 */
@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class OperationAuditAspect {

  private static final String RECORDED = OperationAuditAspect.class.getName();
  private final PlatformTransactionManager transactionManager;
  private final AuditSnapshots snapshots;
  private final AuditRedactor redactor;
  private final OperationLogWriter writer;
  private final com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation correlation;
  private final com.yunfeil161.ailibrary.ai.runtime.AiRuntimeControl controls;

  @Around("@annotation(audit)")
  public Object around(ProceedingJoinPoint point, Audited audit)
    throws Throwable {
    Long id = targetId(point, audit);
    var event = writer.entry(
      audit.resource().name(),
      id,
      ((MethodSignature) point.getSignature()).getMethod().getName()
    );
    try {
      return new TransactionTemplate(transactionManager).execute(status -> {
        // AI回调可能先等待资源锁；实际进入业务写操作前再检查暂停，普通人工请求不受此开关影响。
        var http = request();
        if (
          http != null && correlation.verified(http) != null
        ) controls.requireEnabled("BUSINESS");
        var before = snapshots.read(audit.resource(), event.getTargetId());
        Object result;
        try {
          result = point.proceed();
        } catch (RuntimeException | Error error) {
          throw error;
        } catch (Throwable error) {
          throw new IllegalStateException("业务操作失败", error);
        }
        if (event.getTargetId() == null) event.setTargetId(
          resultId(result, point.getArgs())
        );
        var after = snapshots.read(audit.resource(), event.getTargetId());
        // 幂等重试没有改变业务行时不追加虚假的成功变更。
        if (audit.always() || !java.util.Objects.equals(before, after)) {
          event.setBeforeData(redactor.json(before));
          event.setAfterData(redactor.json(after));
          writer.success(event);
        }
        return result;
      });
    } catch (RuntimeException error) {
      recordFailure(event, error.getClass().getSimpleName());
      throw error;
    }
  }

  /** Controller提前拒绝的越权/格式错误也留痕，不保存请求体、URL查询参数或异常消息。 */
  @AfterReturning(
    "execution(public * com.yunfeil161.ailibrary.common.exception.GlobalExceptionHandler.*(..))"
  )
  public void httpFailure(org.aspectj.lang.JoinPoint point) {
    HttpServletRequest request = request();
    if (
      request == null ||
      request.getAttribute(RECORDED) != null ||
      !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(
        request.getMethod()
      )
    ) return;
    String path = request.getRequestURI();
    if (
      !(
        path.startsWith("/admin/") ||
        path.startsWith("/member-change-applications") ||
        path.startsWith("/borrow-applications") ||
        path.startsWith("/reading-plan") ||
        path.startsWith("/members")
      )
    ) return;
    String error =
      point.getArgs().length > 0
        ? point.getArgs()[0].getClass().getSimpleName()
        : "RequestRejected";
    recordFailure(
      writer.entry("HTTP_OPERATION", null, "HTTP_" + request.getMethod()),
      error
    );
  }

  private void recordFailure(OperationLog event, String errorCode) {
    try {
      writer.failure(event, errorCode);
    } catch (RuntimeException failure) {
      // 不能把已失败业务改报成功，也不能因审计故障覆盖原始HTTP错误。
      log.error(
        "独立失败审计保存失败：eventId={}, action={}",
        event.getEventId(),
        event.getActionCode(),
        failure
      );
    }
    if (request() != null) request().setAttribute(RECORDED, true);
  }

  private Long targetId(ProceedingJoinPoint point, Audited audit) {
    if (audit.id().isBlank()) return null;
    var method = ((MethodSignature) point.getSignature()).getMethod();
    var context = new MethodBasedEvaluationContext(
      null,
      method,
      point.getArgs(),
      new DefaultParameterNameDiscoverer()
    );
    var session = LoginUserContext.getNullable();
    context.setVariable(
      "currentMemberId",
      session == null ? null : session.getMemberId()
    );
    context.setVariable("snapshots", snapshots);
    try {
      return new SpelExpressionParser()
        .parseExpression(audit.id())
        .getValue(context, Long.class);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private Long resultId(Object result, Object[] args) {
    if (result instanceof Long number) return number;
    Object id =
      result instanceof Map<?, ?> map ? map.get("id") : property(result, "id");
    if (id instanceof Number number) return number.longValue();
    for (Object arg : args) {
      id = property(arg, "id");
      if (id instanceof Number number) return number.longValue();
    }
    return null;
  }

  private Object property(Object object, String name) {
    if (object == null) return null;
    var bean = new BeanWrapperImpl(object);
    return bean.isReadableProperty(name) ? bean.getPropertyValue(name) : null;
  }

  private HttpServletRequest request() {
    return RequestContextHolder.getRequestAttributes() instanceof
      ServletRequestAttributes attributes
      ? attributes.getRequest()
      : null;
  }
}
