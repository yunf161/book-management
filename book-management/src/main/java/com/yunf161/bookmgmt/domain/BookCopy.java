package com.yunf161.bookmgmt.domain;

/**
 * 图书副本实体：一条书目可对应多个可借阅副本。
 */
public class BookCopy {
    /** 副本唯一编号。 */
    private String copyId;
    /** 关联书目 ISBN。 */
    private String isbn;
    /** 馆藏位置。 */
    private String location;
    /** 副本状态。 */
    private BookCopyStatus status;

    /** 新建副本时默认可借。 */
    public BookCopy(String copyId, String isbn, String location) {
        this.copyId = copyId;
        this.isbn = isbn;
        this.location = location;
        this.status = BookCopyStatus.AVAILABLE;
    }

    /** 获取副本编号。 */
    public String getCopyId() {
        return copyId;
    }

    /** 设置副本编号。 */
    public void setCopyId(String copyId) {
        this.copyId = copyId;
    }

    /** 获取关联 ISBN。 */
    public String getIsbn() {
        return isbn;
    }

    /** 设置关联 ISBN。 */
    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    /** 获取馆藏位置。 */
    public String getLocation() {
        return location;
    }

    /** 设置馆藏位置。 */
    public void setLocation(String location) {
        this.location = location;
    }

    /** 获取副本状态。 */
    public BookCopyStatus getStatus() {
        return status;
    }

    /** 设置副本状态。 */
    public void setStatus(BookCopyStatus status) {
        this.status = status;
    }
}
