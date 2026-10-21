package com.yunfeil161.ailibrary.audit;

import com.github.pagehelper.*;
import com.yunfeil161.ailibrary.common.api.*;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 分页留在Service层，鉴权同时覆盖直接内部调用。 */
@Service
@RequiredArgsConstructor
public class OperationLogQueryService {

  private final PermissionAccess access;
  private final OperationLogMapper mapper;
  private final AuditRedactor redactor;

  public PageResult<OperationLog> query(OperationLogQuery q) {
    access.require(PermissionCodes.OPERATION_LOG_QUERY, DataScope.ALL);
    if (
      q.getCreatedFrom() != null &&
      q.getCreatedTo() != null &&
      q.getCreatedFrom().isAfter(q.getCreatedTo())
    ) throw new WrongFormatException("开始时间不能晚于结束时间");
    if (
      (q.getId() != null && q.getId() <= 0) ||
      (q.getTargetId() != null && q.getTargetId() <= 0) ||
      (q.getActorMemberId() != null && q.getActorMemberId() <= 0) ||
      (q.getActorRoleId() != null && q.getActorRoleId() <= 0)
    ) throw new WrongFormatException("记录、操作者、角色和对象ID必须大于0");
    if (
      q.getResultStatus() != null &&
      !java.util.Set.of("SUCCESS", "FAILURE").contains(q.getResultStatus())
    ) throw new WrongFormatException("审计结果只能为SUCCESS或FAILURE");
    if (
      q.getActorKind() != null &&
      !java.util.Set.of("MEMBER", "SYSTEM", "ANONYMOUS").contains(
        q.getActorKind()
      )
    ) throw new WrongFormatException(
      "执行者类型只能为MEMBER、SYSTEM或ANONYMOUS"
    );
    PageHelper.startPage(
      q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage(),
      q.getPageSize() == null || q.getPageSize() < 1
        ? 10
        : Math.min(q.getPageSize(), 100)
    );
    var page = new PageInfo<>(mapper.find(q));
    page.getList().forEach(log -> {
      log.setBeforeData(redactor.stored(log.getBeforeData()));
      log.setAfterData(redactor.stored(log.getAfterData()));
      // 老记录的自由文本原因可能含个人信息，历史原因统一收起。
      log.setReason("业务操作：" + log.getActionCode());
    });
    return new PageResult<>(page.getTotal(), page.getList());
  }
}
