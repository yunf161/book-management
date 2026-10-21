package com.yunfeil161.ailibrary.recommendation.dto;

import java.util.List;
import lombok.Data;

/** 标签全量替换请求；空列表表示清除，null 表示格式错误。 */
@Data
public class TagsRequest {

  private List<Long> tagIds;
}
