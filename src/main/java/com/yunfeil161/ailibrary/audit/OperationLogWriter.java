package com.yunfeil161.ailibrary.audit;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 成功审计加入业务事务；失败审计独立提交，且不读取被回滚事务锁住的业务行。 */
@Service
@RequiredArgsConstructor
public class OperationLogWriter {

  private final OperationLogMapper mapper;
  private final com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation correlation;

  public OperationLog entry(String type, Long id, String action) {
    var log = new OperationLog();
    var session = LoginUserContext.getNullable();
    log.setEventId(UUID.randomUUID().toString());
    if (
      org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof
        org.springframework.web.context.request.ServletRequestAttributes attributes
    ) {
      String actionId = correlation.verified(attributes.getRequest());
      if (actionId != null) log.setEventId(actionId);
    }
    log.setActorKind(
      session == null
        ? org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() ==
          null
          ? "SYSTEM"
          : "ANONYMOUS"
        : "MEMBER"
    );
    if (session != null) {
      log.setActorMemberId(session.getMemberId());
      log.setActorMemberType(
        session.getMemberType() == null ? null : session.getMemberType().name()
      );
      log.setActorRoleId(session.getRoleId());
    }
    log.setTargetType(type);
    log.setTargetId(id != null && id > 0 ? id : null);
    log.setActionCode(action);
    log.setReason("执行业务操作：" + action);
    log.setCreatedAt(LocalDateTime.now());
    return log;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void success(OperationLog log) {
    log.setResultStatus("SUCCESS");
    insert(log);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failure(OperationLog log, String errorCode) {
    log.setResultStatus("FAILURE");
    log.setErrorCode(errorCode);
    log.setBeforeData(null);
    log.setAfterData(null);
    insert(log);
  }

  private void insert(OperationLog log) {
    if (mapper.insert(log) != 1) throw new IllegalStateException(
      "保存操作审计失败"
    );
  }
}
