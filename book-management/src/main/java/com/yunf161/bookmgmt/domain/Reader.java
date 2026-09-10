package com.yunf161.bookmgmt.domain;


/**
 * 读者实体，承载读者基本信息与借阅约束参数。
 */
public class Reader extends Person {
    /** 读者唯一编号。 */
    private final String readerId;
    /** 当前账号状态。 */
    private ReaderStatus status;
    /** 最大可同时借阅数量。 */
    private int maxBorrowedBooks;
    /** 累计逾期次数。 */
    private int overdueCount;

    /** 构造读者。 */
    public Reader(String readerId, ReaderStatus status, int maxBorrowedBooks) {
        this.readerId = readerId;
        this.status = status;
        this.maxBorrowedBooks = maxBorrowedBooks;
    }

    /** 展示读者信息。 */
    @Override
    public void displayInfo() {
        System.out.println("Reader Info:");
        System.out.println("Name: " + getName());
        System.out.println("Reader ID: " + getReaderId());
        System.out.println("Age: " + getAge());
        System.out.println("Phone Number: " + getPhone_number());
        System.out.println("Max Borrowed Books: " + getMaxBorrowedBooks());
        System.out.println("Overdue Count: " + getOverdueCount());
        System.out.println("Status: " + getStatus().getDescription());
    }

    /** 获取读者编号。 */
    public String getReaderId() {
        return readerId;
    }

    /** 获取读者状态。 */
    public ReaderStatus getStatus() {
        return status;
    }

    /** 设置读者状态。 */
    public void setStatus(ReaderStatus status) {
        this.status = status;
    }

    /** 获取借阅上限。 */
    public int getMaxBorrowedBooks() {
        return maxBorrowedBooks;
    }

    /** 设置借阅上限。 */
    public void setMaxBorrowedBooks(int maxBorrowedBooks) {
        this.maxBorrowedBooks = maxBorrowedBooks;
    }

    /** 获取累计逾期次数。 */
    public int getOverdueCount() {
        return overdueCount;
    }

    /** 设置累计逾期次数。 */
    public void setOverdueCount(int overdueCount) {
        this.overdueCount = overdueCount;
    }
}