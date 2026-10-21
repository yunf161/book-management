package com.yunfeil161.ailibrary.bootstrap;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 全新安装的离线引导工具，不提供HTTP接口，也不开放现有管理员增删改。 */
public final class AdminBootstrap {

  public static void main(String[] args) throws Exception {
    String url = required("MYSQL_URL"),
      username = required("MYSQL_USERNAME"),
      databasePassword = required("MYSQL_PASSWORD");
    String email = required("BOOTSTRAP_ADMIN_EMAIL")
      .trim()
      .toLowerCase(Locale.ROOT);
    String password = required("BOOTSTRAP_ADMIN_PASSWORD"),
      name = required("BOOTSTRAP_ADMIN_NAME").trim();
    if (
      !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") ||
      email.length() > 254 ||
      name.isBlank() ||
      name.length() > 80 ||
      password.length() < 8 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) throw new IllegalArgumentException("初始管理员邮箱、姓名或密码格式错误");
    String hash = new BCryptPasswordEncoder().encode(password);
    try (var c = DriverManager.getConnection(url, username, databasePassword)) {
      c.setAutoCommit(false);
      try (
        var query = c.createStatement();
        var rows = query.executeQuery(
          "select get_lock('ai-library:first-admin',10)"
        )
      ) {
        rows.next();
        if (rows.getInt(1) != 1) throw new IllegalStateException(
          "其他初始化进程正在执行"
        );
      }
      // 仅允许空成员库。现有部署、停用账号、角色授权仍由原业务流程管理。
      try (
        var query = c.createStatement();
        var rows = query.executeQuery("select count(*) from member")
      ) {
        rows.next();
        if (rows.getLong(1) != 0) throw new IllegalStateException(
          "已有成员数据，拒绝运行首次管理员引导"
        );
      }
      long role;
      try (
        var query = c.createStatement();
        var rows = query.executeQuery(
          "select id from role where code='ADMIN' and status='ACTIVE'"
        )
      ) {
        if (!rows.next()) throw new IllegalStateException("缺少有效管理员角色");
        role = rows.getLong(1);
      }
      try (var query = c.createStatement()) {
        query.executeUpdate(
          "insert into department(code,name,description,status,version) select 'SYSTEM','系统管理','首次安装管理部门','ACTIVE',0 where not exists(select 1 from department where code='SYSTEM')"
        );
      }
      long department;
      try (
        var query = c.createStatement();
        var rows = query.executeQuery(
          "select id from department where code='SYSTEM'"
        )
      ) {
        rows.next();
        department = rows.getLong(1);
      }
      long member;
      try (
        var query = c.prepareStatement(
          "insert into member(member_type,role_id,name,email,password_hash,account_status,qualification_status,email_verified_at,borrowed_book_count,permanent_borrow_limit) values('EMPLOYEE',?,?,?,?,'ACTIVE','NOT_APPLIED',current_timestamp(3),0,5)",
          Statement.RETURN_GENERATED_KEYS
        )
      ) {
        query.setLong(1, role);
        query.setString(2, name);
        query.setString(3, email);
        query.setString(4, hash);
        query.executeUpdate();
        try (var rows = query.getGeneratedKeys()) {
          rows.next();
          member = rows.getLong(1);
        }
      }
      try (
        var query = c.prepareStatement(
          "insert into employee_profile(member_id,employee_no,department_id,position_name,employment_status,hired_on) values(?,?,?,'系统管理员','ACTIVE',current_date)"
        )
      ) {
        query.setLong(1, member);
        query.setString(2, "EMP-" + String.format("%08d", member));
        query.setLong(3, department);
        query.executeUpdate();
      }
      c.commit();
      System.out.println(
        "初始管理员已创建；密码不会输出。首次登录仍需确认借阅须知。"
      );
      // JDBC连接关闭时释放MySQL命名锁，失败路径也不会留下初始化锁。
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException(
      "请设置环境变量：" + name
    );
    return value;
  }
}
