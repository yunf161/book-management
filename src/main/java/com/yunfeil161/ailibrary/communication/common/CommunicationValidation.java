package com.yunfeil161.ailibrary.communication.common;

import com.github.pagehelper.PageHelper;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import java.time.LocalDateTime;
import java.util.Set;

/** 通信输入边界；必须在启动PageHelper之前完成关联查询。 */
public final class CommunicationValidation {
  private CommunicationValidation() {}

  public static void id(Long value) {
    if (value != null && value < 1) throw new WrongFormatException("ID必须大于0");
  }

  public static void text(String value, int max, boolean required) {
    if (required && (value == null || value.isBlank())) throw new WrongFormatException("必填字段不能为空");
    if (value != null && value.length() > max) throw new WrongFormatException("字段超过最大长度" + max);
  }

  public static void choice(String value, String... allowed) {
    if (value != null && !Set.of(allowed).contains(value))
      throw new WrongFormatException("状态或类型不合法");
  }

  public static void range(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to))
      throw new WrongFormatException("开始时间不能晚于结束时间");
  }

  public static void page(PageQuery q) {
    int page = q.getPage() == null ? 1 : q.getPage();
    int size = q.getPageSize() == null ? 20 : q.getPageSize();
    if (page < 1 || size < 1 || size > 100) throw new WrongFormatException("分页参数不合法");
    PageHelper.startPage(page, size);
  }

  public static void one(int affected) {
    if (affected != 1) throw new IllegalStateException("预期影响一条记录，实际=" + affected);
  }
}
