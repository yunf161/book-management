package com.yunfeil161.ailibrary.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.*;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 直接管理、本人资料和申请审批三条路径的字段边界，不依赖数字角色等级。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:member_maintenance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
class MemberMaintenanceIntegrationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  LoginSessionService sessions;

  String tag;
  Long reader, employee, staff, admin, department;

  @BeforeEach
  void setup() {
    tag = "MAINT-" + UUID.randomUUID().toString().substring(0, 12);
    jdbc.update("insert into department(code,name) values(?,?)", tag, tag);
    department = jdbc.queryForObject(
      "select id from department where code=?",
      Long.class,
      tag
    );
    reader = member("reader", "READER", "READER");
    employee = member("employee", "EMPLOYEE", "LIBRARIAN");
    staff = member("staff", "EMPLOYEE", "LIBRARIAN");
    admin = member("admin", "EMPLOYEE", "ADMIN");
    for (Long id : List.of(employee, staff, admin)) {
      jdbc.update(
        "insert into employee_profile(member_id,employee_no,department_id,position_name,employment_status,hired_on) values(?,?,?,'馆员','ACTIVE',current_date)",
        id,
        tag + id,
        department
      );
    }
    jdbc.update("insert into reader_settings(member_id) values(?)", reader);
  }

  Long member(String suffix, String type, String role) {
    jdbc.update(
      "insert into member(name,member_type,role_id,account_status,qualification_status,permanent_borrow_limit) values(?,?,?,'ACTIVE','APPROVED',5)",
      tag + suffix,
      type,
      role(role)
    );
    return jdbc.queryForObject(
      "select id from member where name=?",
      Long.class,
      tag + suffix
    );
  }

  Long role(String code) {
    return jdbc.queryForObject(
      "select id from role where code=?",
      Long.class,
      code
    );
  }

  void login(String role, Long id) {
    LoginUserContext.set(
      new LoginSession(
        "test",
        id,
        role(role),
        role.equals("READER") ? MemberType.READER : MemberType.EMPLOYEE,
        role,
        "test@example.test",
        "验收"
      )
    );
  }

  void update(Long id, String type, String fields, int status)
    throws Exception {
    mvc
      .perform(
        put("/admin/members/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"memberType\":\"" + type + "\"," + fields + "}")
      )
      .andExpect(status().is(status));
  }

  @AfterEach
  void cleanup() {
    LoginUserContext.clear();
    jdbc.update("delete from reader_settings where member_id=?", reader);
    jdbc.update(
      "delete from employee_profile where member_id in (?,?,?)",
      employee,
      staff,
      admin
    );
    jdbc.update(
      "delete from member where id in (?,?,?,?)",
      reader,
      employee,
      staff,
      admin
    );
    jdbc.update("delete from department where id=?", department);
  }

  @Test
  void librarianCanMaintainReaderQuotaButNotAccountOrEmployee()
    throws Exception {
    login("LIBRARIAN", staff);
    update(
      reader,
      "READER",
      "\"permanentBorrowLimit\":7,\"expectedVersion\":0",
      200
    );
    assertEquals(
      7,
      jdbc.queryForObject(
        "select permanent_borrow_limit from member where id=?",
        Integer.class,
        reader
      )
    );
    update(
      reader,
      "READER",
      "\"accountStatus\":\"DISABLED\",\"expectedVersion\":1",
      403
    );
    update(
      employee,
      "EMPLOYEE",
      "\"permanentBorrowLimit\":7,\"expectedVersion\":0",
      403
    );
  }

  @Test
  void existingQualificationCanBeSuspendedAndRestoredButInitialApprovalCannotBeBypassed()
    throws Exception {
    login("LIBRARIAN", staff);
    update(
      reader,
      "READER",
      "\"qualificationStatus\":\"SUSPENDED\",\"expectedVersion\":0",
      200
    );
    update(
      reader,
      "READER",
      "\"qualificationStatus\":\"APPROVED\",\"expectedVersion\":1",
      200
    );
    jdbc.update(
      "update member set qualification_status='NOT_APPLIED' where id=?",
      reader
    );
    update(
      reader,
      "READER",
      "\"qualificationStatus\":\"APPROVED\",\"expectedVersion\":2",
      409
    );
    update(
      reader,
      "READER",
      "\"qualificationStatus\":\"PENDING\",\"expectedVersion\":2",
      409
    );
  }

  @Test
  void administratorCanDisableAndEnableAccountAndDisableRevokesSessions()
    throws Exception {
    login("ADMIN", admin);
    String jti = UUID.randomUUID().toString();
    sessions.save(
      jti,
      new LoginSession(
        jti,
        reader,
        role("READER"),
        MemberType.READER,
        "READER",
        "test@example.test",
        "验收"
      ),
      Duration.ofMinutes(5)
    );
    assertNotNull(sessions.find(jti));
    update(
      reader,
      "READER",
      "\"accountStatus\":\"DISABLED\",\"expectedVersion\":0",
      200
    );
    assertNull(sessions.find(jti));
    update(
      reader,
      "READER",
      "\"accountStatus\":\"ACTIVE\",\"expectedVersion\":1",
      200
    );
    assertEquals(
      "ACTIVE",
      jdbc.queryForObject(
        "select account_status from member where id=?",
        String.class,
        reader
      )
    );
  }

  @Test
  void administratorCanMaintainOrdinaryEmployeeQuotaButPersonnelAndRoleStillRequireApproval()
    throws Exception {
    login("ADMIN", admin);
    update(
      employee,
      "EMPLOYEE",
      "\"permanentBorrowLimit\":8,\"expectedVersion\":0",
      200
    );
    update(
      employee,
      "EMPLOYEE",
      "\"departmentId\":" + department + ",\"expectedVersion\":1",
      403
    );
    update(
      employee,
      "EMPLOYEE",
      "\"roleId\":" + role("ADMIN") + ",\"expectedVersion\":1",
      403
    );
    update(
      employee,
      "EMPLOYEE",
      "\"employmentStatus\":\"SUSPENDED\",\"expectedVersion\":1",
      403
    );
  }

  @Test
  void readonlyIdentityAndTemporaryQuotaCannotBeSmuggledAlongsideAllowedQuota()
    throws Exception {
    login("ADMIN", admin);
    for (String field : List.of(
      "\"name\":\"代改\"",
      "\"email\":\"new@example.test\"",
      "\"passwordHash\":\"hash\"",
      "\"temporaryBorrowLimit\":20",
      "\"acceptedRuleVersion\":\"fake\""
    )) {
      update(
        reader,
        "READER",
        field + ",\"permanentBorrowLimit\":6,\"expectedVersion\":0",
        403
      );
    }
    assertEquals(
      0L,
      jdbc.queryForObject(
        "select version from member where id=?",
        Long.class,
        reader
      )
    );
  }

  @Test
  void selfProfileIsEditableButSelfGrantAndOtherReadersAreNot()
    throws Exception {
    login("READER", reader);
    update(
      reader,
      "READER",
      "\"name\":\"" +
        tag +
        "self\",\"permanentBorrowLimit\":20,\"expectedVersion\":0",
      200
    );
    assertEquals(
      5,
      jdbc.queryForObject(
        "select permanent_borrow_limit from member where id=?",
        Integer.class,
        reader
      )
    );
    update(
      employee,
      "EMPLOYEE",
      "\"permanentBorrowLimit\":20,\"expectedVersion\":0",
      403
    );
  }

  @Test
  void quotaBoundsVersionAndActualMemberTypeRemainEnforced() throws Exception {
    login("LIBRARIAN", staff);
    jdbc.update("update member set borrowed_book_count=4 where id=?", reader);
    update(
      reader,
      "READER",
      "\"permanentBorrowLimit\":3,\"expectedVersion\":0",
      409
    );
    update(
      reader,
      "READER",
      "\"permanentBorrowLimit\":-1,\"expectedVersion\":0",
      400
    );
    update(
      reader,
      "READER",
      "\"permanentBorrowLimit\":6,\"expectedVersion\":0",
      200
    );
    update(
      reader,
      "READER",
      "\"permanentBorrowLimit\":7,\"expectedVersion\":0",
      409
    );
    update(
      reader,
      "EMPLOYEE",
      "\"permanentBorrowLimit\":7,\"expectedVersion\":1",
      400
    );
  }

  @Test
  void logicalDisableRequiresAccountAuthorityAndAdminProtectionRemains()
    throws Exception {
    login("LIBRARIAN", staff);
    mvc
      .perform(delete("/admin/members/" + reader).param("memberType", "READER"))
      .andExpect(status().isForbidden());
    login("ADMIN", admin);
    mvc
      .perform(
        delete("/admin/members/" + employee).param("memberType", "EMPLOYEE")
      )
      .andExpect(status().isOk());
    assertEquals(
      "DISABLED",
      jdbc.queryForObject(
        "select account_status from member where id=?",
        String.class,
        employee
      )
    );
    Long secondAdmin = member("second-admin", "EMPLOYEE", "ADMIN");
    try {
      update(
        secondAdmin,
        "EMPLOYEE",
        "\"permanentBorrowLimit\":6,\"expectedVersion\":0",
        400
      );
    } finally {
      jdbc.update("delete from member where id=?", secondAdmin);
    }
  }
}
