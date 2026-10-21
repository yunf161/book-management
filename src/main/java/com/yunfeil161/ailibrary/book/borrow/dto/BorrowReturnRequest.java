package com.yunfeil161.ailibrary.book.borrow.dto;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome;
import lombok.Data;

/**
 * 馆员办理归还时提交的参数。
 */
@Data
public class BorrowReturnRequest {

  /** 本次归还/结案结果；不传时按NORMAL处理，兼容原归还请求。 */
  private BorrowReturnOutcome returnOutcome;
  /** 归还时记录的副本状况。 */
  private String returnConditionNote;
  /** 客户端读取到的版本号，用于乐观锁校验。 */
  private Long expectedVersion;
}
