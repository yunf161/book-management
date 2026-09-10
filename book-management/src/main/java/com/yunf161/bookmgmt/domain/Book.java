package com.yunf161.bookmgmt.domain;

/**
 * 书目信息实体，只保存图书的基础元数据。
 */
public class Book {
    /** 国际标准书号，作为书目标识。 */
    private String isbn;
    /** 书名。 */
    private String title;
    /** 作者。 */
    private String author;
    /** 分类。 */
    private String category;
    /** 出版年份。 */
    private int publicationYear;

    /** 构造书目信息。 */
    public Book(String isbn, String title, String author, String category, int publicationYear) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.category = category;
        this.publicationYear = publicationYear;
    }

    /** 获取 ISBN。 */
    public String getIsbn() {
        return isbn;
    }

    /** 设置 ISBN。 */
    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    /** 获取书名。 */
    public String getTitle() {
        return title;
    }

    /** 设置书名。 */
    public void setTitle(String title) {
        this.title = title;
    }

    /** 获取作者。 */
    public String getAuthor() {
        return author;
    }

    /** 设置作者。 */
    public void setAuthor(String author) {
        this.author = author;
    }

    /** 获取分类。 */
    public String getCategory() {
        return category;
    }

    /** 设置分类。 */
    public void setCategory(String category) {
        this.category = category;
    }

    /** 获取出版年份。 */
    public int getPublicationYear() {
        return publicationYear;
    }

    /** 设置出版年份。 */
    public void setPublicationYear(int publicationYear) {
        this.publicationYear = publicationYear;
    }
}
