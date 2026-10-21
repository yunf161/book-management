package com.yunfeil161.ailibrary.ai.handoff;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人、馆员共用聚合接口，Service强制裁剪数据范围。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiHandoffQuery extends PageQuery {

  private Long id;
  private Long memberId;
  private Long staffMemberId;
  private String status;
}
