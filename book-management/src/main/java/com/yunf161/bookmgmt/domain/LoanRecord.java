package com.yunf161.bookmgmt.domain;

import java.time.LocalDateTime;

/**
 * 借阅记录实体，记录借出、应还、归还与状态流转。
 */
public class LoanRecord {
    /** 借阅记录唯一编号。 */
    private String recordId;
    /** 读者编号。 */
    private String readerId;
    /** 副本编号。 */
    private String copyId;
    /** 借出时间。 */
    private LocalDateTime borrowTime;
    /** 应还时间。 */
    private LocalDateTime dueTime;
    /** 归还时间。 */
    private LocalDateTime returnTime;
    /** 借阅状态。 */
    private LoanStatus status;

    /** 构造借阅记录，默认状态为借阅中。 */
    public LoanRecord(String recordId, String readerId, String copyId, LocalDateTime borrowTime, LocalDateTime dueTime) {
        this.recordId = recordId;
        this.readerId = readerId;
        this.copyId = copyId;
        this.borrowTime = borrowTime;
        this.dueTime = dueTime;
        this.status = LoanStatus.BORROWING;
    }

    /** 获取借阅记录编号。 */
    public String getRecordId() {
        return recordId;
    }

    /** 设置借阅记录编号。 */
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /** 获取读者编号。 */
    public String getReaderId() {
        return readerId;
    }

    /** 设置读者编号。 */
    public void setReaderId(String readerId) {
        this.readerId = readerId;
    }

    /** 获取副本编号。 */
    public String getCopyId() {
        return copyId;
    }

    /** 设置副本编号。 */
    public void setCopyId(String copyId) {
        this.copyId = copyId;
    }

    /** 获取借出时间。 */
    public LocalDateTime getBorrowTime() {
        return borrowTime;
    }

    /** 设置借出时间。 */
    public void setBorrowTime(LocalDateTime borrowTime) {
        this.borrowTime = borrowTime;
    }

    /** 获取应还时间。 */
    public LocalDateTime getDueTime() {
        return dueTime;
    }

    /** 设置应还时间。 */
    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    /** 获取归还时间。 */
    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    /** 设置归还时间。 */
    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    /** 获取借阅状态。 */
    public LoanStatus getStatus() {
        return status;
    }

    /** 设置借阅状态。 */
    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    /** 判断当前记录是否逾期（仅借阅中记录参与判断）。 */
    public boolean isOverdue(LocalDateTime now) {
        return status == LoanStatus.BORROWING && now.isAfter(dueTime);
    }
}
