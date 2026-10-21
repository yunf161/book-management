package com.yunfeil161.ailibrary.book.catalog.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 带分类和实时库存统计的图书查询结果。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookDetailVO extends Book {

  /** 分类展示名称，二级分类使用“父分类：子分类”格式。 */
  private String category;
  /** 兴趣标签从book_tag关联查询，不落库为书目字段。 */
  private java.util.List<com.yunfeil161.ailibrary.recommendation.entity.InterestTag> tags;
  private Long totalCopies;
  private Long availableCopies;
  private String coverUrl;
}
