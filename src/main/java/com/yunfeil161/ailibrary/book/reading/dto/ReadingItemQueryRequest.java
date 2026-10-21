package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.*;
import lombok.Data;

/** 聚合查询阅读条目参数，排序值由 Service 白名单转换。 */
@Data
public class ReadingItemQueryRequest {

  private Integer pageNum;
  private Integer pageSize;
  private Long memberId;
  private Long bookId;
  private String memberKeyword;
  private String bookKeyword;
  private ReadingItemStatus status;
  private ReadingItemSource source;
  private String orderBy;
  private String orderDirection;
  private String sortColumn;
  private String sortDirection;
}
