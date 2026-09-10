package com.yunf161.bookmgmt.domain;

/**
 * 馆员实体，记录工作人员身份与角色信息。
 */
public class Librarian extends Person {
    /** 工号。 */
    private String employeeCode;
    /** 馆员状态。 */
    private LibrarianStatus status;
    /** 角色（如 ADMIN / OPERATOR）。 */
    private String role;

    /** 构造馆员。 */
    public Librarian(String employeeCode, LibrarianStatus status, String role) {
        this.employeeCode = employeeCode;
        this.status = status;
        this.role = role;
    }

    /** 展示馆员信息。 */
    @Override
    public void displayInfo() {
        System.out.println("Librarian Info:");
        System.out.println("Name: " + getName());
        System.out.println("Age: " + getAge());
        System.out.println("Phone Number: " + getPhone_number());
        System.out.println("Employee Code: " + employeeCode);
        System.out.println("Status: " + status.getDescription());
        System.out.println("Role: " + role);
    }

    /** 获取工号。 */
    public String getEmployeeCode() {
        return employeeCode;
    }

    /** 设置工号。 */
    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    /** 获取馆员状态。 */
    public LibrarianStatus getStatus() {
        return status;
    }

    /** 设置馆员状态。 */
    public void setStatus(LibrarianStatus status) {
        this.status = status;
    }

    /** 获取角色。 */
    public String getRole() {
        return role;
    }

    /** 设置角色。 */
    public void setRole(String role) {
        this.role = role;
    }
}
