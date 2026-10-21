package com.yunfeil161.ailibrary.recommendation.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 独立修改书目标签时必须提供书目版本，避免覆盖并发编辑。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookTagsRequest extends TagsRequest {

  private Long expectedVersion;
}
