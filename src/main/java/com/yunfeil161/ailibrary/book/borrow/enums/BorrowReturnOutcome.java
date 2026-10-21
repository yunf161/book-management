package com.yunfeil161.ailibrary.book.borrow.enums;

/** 结案时的实物结果；独立于借阅状态，损坏归还仍然属于已归还。 */
public enum BorrowReturnOutcome {
  /** 正常归还，副本重新可借。 */
  NORMAL,
  /** 损坏归还，副本进入维护。 */
  DAMAGED,
  /** 确认遗失并结案，副本标记遗失。 */
  LOST,
}
