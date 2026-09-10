package com.yunf161.bookmgmt.domain;

/**
 * 人员抽象基类，沉淀读者和馆员共有属性。
 */
public abstract class Person {
    /** 姓名。 */
    private String name;
    /** 年龄。 */
    private int age;
    /** 手机号。 */
    private String phone_number;


    /** 获取姓名。 */
    public String getName() {
        return name;
    }

    /** 设置姓名。 */
    public void setName(String name) {
        this.name = name;
    }


    /** 获取年龄。 */
    public int getAge() {
        return age;
    }

    /** 设置年龄。 */
    public void setAge(int age) {
        this.age = age;
    }

    /** 获取手机号。 */
    public String getPhone_number() {
        return phone_number;
    }

    /** 设置手机号。 */
    public void setPhone_number(String phone_number) {
        this.phone_number = phone_number;
    }

    /** 子类实现各自的信息展示。 */
    public abstract void displayInfo();
}

