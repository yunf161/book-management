package com.yunfeil161.ailibrary.audit;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 单一聚合分页查询，所有条件可选并可组合。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQuery extends PageQuery {

  private Long id;
  private Long actorMemberId;
  private Long actorRoleId;
  private String actorKind;
  private String targetType;
  private Long targetId;
  private String actionCode;
  private String resultStatus;
  private LocalDateTime createdFrom;
  private LocalDateTime createdTo;
}
