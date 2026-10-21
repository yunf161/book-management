package com.yunfeil161.ailibrary.policy.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 保持项目聚合查询：按ID筛选仍返回分页结构。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PolicyQueryRequest extends PageQuery {

  private Long id;
  private String type;
  private String status;
  private String title;
  private Long policyVersion;
}
