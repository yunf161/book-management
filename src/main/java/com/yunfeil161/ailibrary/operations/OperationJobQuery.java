package com.yunfeil161.ailibrary.operations;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperationJobQuery extends PageQuery {

  private String jobId;
  private String type;
  private String state;
}
