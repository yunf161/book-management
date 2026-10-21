package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人和管理端复用同一个聚合查询，数据范围由角色授权决定。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskQuery extends PageQuery {

  private String taskId;
  private Long memberId;
  private String state;
  private String type;
}
