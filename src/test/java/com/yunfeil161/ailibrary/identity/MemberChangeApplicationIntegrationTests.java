package com.yunfeil161.ailibrary.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 成员变更申请从提交、查询到人工审批落库的HTTP闭环测试。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:member_change_application_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class MemberChangeApplicationIntegrationTests {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  private Long readerId;
  private Long secondReaderId;
  private Long librarianId;
  private Long adminId;
  private Long secondAdminId;

  @BeforeEach
  void setUp() {
    jdbc.update(
      "insert into department(code,name,status,version) values('CHANGE','变更审批部','ACTIVE',0)"
    );
    Long departmentId = jdbc.queryForObject(
      "select id from department where code='CHANGE'",
      Long.class
    );
    readerId = insertMember(
      "READER",
      "READER",
      "申请读者",
      "change.reader@example.test",
      "NOT_APPLIED",
      0,
      5
    );
    secondReaderId = insertMember(
      "READER",
      "READER",
      "其他读者",
      "change.other@example.test",
      "NOT_APPLIED",
      0,
      5
    );
    librarianId = insertMember(
      "EMPLOYEE",
      "LIBRARIAN",
      "审批馆员",
      "change.librarian@example.test",
      "NOT_APPLIED",
      0,
      5
    );
    adminId = insertMember(
      "EMPLOYEE",
      "ADMIN",
      "审批管理员",
      "change.admin@example.test",
      "APPROVED",
      0,
      8
    );
    secondAdminId = insertMember(
      "EMPLOYEE",
      "ADMIN",
      "第二管理员",
      "change.admin2@example.test",
      "APPROVED",
      0,
      8
    );
    jdbc.update(
      "update member set accepted_rule_version='BORROWING-1',email_verified_at=current_timestamp where email like 'change.%'"
    );
    insertEmployee(librarianId, "CHANGE-L001", departmentId, "馆员");
    insertEmployee(adminId, "CHANGE-A001", departmentId, "管理员");
    insertEmployee(secondAdminId, "CHANGE-A002", departmentId, "管理员");
  }

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  @Test
  void newReaderApplicationSnapshotsVersionWithoutApplicantInput()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    long id = create(
      """
      {"applicationType":"READER_QUALIFICATION","reason":"自动快照版本", "acceptedRuleVersion":"BORROWING-1"}
      """
    );
    // 资格进入待审后成员版本递增，审批快照必须记录递增后的版本。
    assertValue(
      "select expected_member_version from member_change_application where id=?",
      id,
      1L
    );
  }

  @Test
  void newEmployeeApplicationSnapshotsBothVersionsAndStillRejectsStaleClients()
    throws Exception {
    jdbc.update("update member set version=4 where id=?", librarianId);
    jdbc.update(
      "update employee_profile set version=6 where member_id=?",
      librarianId
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long id = create(
      """
      {"applicationType":"EMPLOYEE_PROFILE_CHANGE","reason":"申请调整职务","requestedPositionName":"新职务"}
      """
    );
    assertValue(
      "select expected_member_version from member_change_application where id=?",
      id,
      4L
    );
    assertValue(
      "select expected_profile_version from member_change_application where id=?",
      id,
      6L
    );
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"applicationType":"EMPLOYEE_PROFILE_CHANGE","reason":"旧客户端","requestedPositionName":"职务","expectedMemberVersion":3}
            """
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void readerQualificationCanBeSubmittedQueriedAndApprovedByLibrarian()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    long applicationId = create(
      "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"申请借阅\"," +
        "\"acceptedRuleVersion\":\"BORROWING-1\",\"expectedMemberVersion\":0}"
    );
    assertValue(
      "select qualification_status from member where id=?",
      readerId,
      "PENDING"
    );

    // 即使伪造其他成员ID，SELF查询也只返回当前读者自己的申请。
    mvc
      .perform(
        get("/member-change-applications").param(
          "applicantMemberId",
          String.valueOf(secondReaderId)
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].applicantMemberId").value(readerId));

    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"decision\":\"APPROVE\",\"reason\":\"符合规则\",\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("APPROVED"));

    assertValue(
      "select qualification_status from member where id=?",
      readerId,
      "APPROVED"
    );
    assertValue(
      "select accepted_rule_version from member where id=?",
      readerId,
      "BORROWING-1"
    );
    assertValue(
      "select business_type from message where business_id=? and notification_key like 'member-change:%'",
      applicationId,
      "MEMBER_CHANGE"
    );
    // 审批修改成员与申请时，必须在同一事务留下审批前后审计，不能只记录提交。
    assertValue(
      "select action_code from member_operation_log where target_type='MEMBER_CHANGE' and target_id=? and action_code='decide' and result_status='SUCCESS'",
      applicationId,
      "decide"
    );
  }

  @Test
  void rejectedAndCancelledQualificationRestoreCorrectMemberState()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    long rejected = create(
      "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"申请资格\"," +
        "\"acceptedRuleVersion\":\"BORROWING-1\",\"expectedMemberVersion\":0}"
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", rejected)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"decision\":\"REJECT\",\"reason\":\"资料不足\",\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk());
    assertValue(
      "select qualification_status from member where id=?",
      readerId,
      "REJECTED"
    );
    assertValue(
      "select accepted_rule_version from member where id=?",
      readerId,
      "BORROWING-1"
    );

    long currentVersion = jdbc.queryForObject(
      "select version from member where id=?",
      Long.class,
      readerId
    );
    login(readerId, "READER", MemberType.READER);
    long cancelled = create(
      "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"再次申请\"," +
        "\"acceptedRuleVersion\":\"BORROWING-1\",\"expectedMemberVersion\":" +
        currentVersion +
        "}"
    );
    mvc
      .perform(
        post("/member-change-applications/{id}/cancel", cancelled)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    assertValue(
      "select qualification_status from member where id=?",
      readerId,
      "REJECTED"
    );
    assertValue(
      "select accepted_rule_version from member where id=?",
      readerId,
      "BORROWING-1"
    );
  }

  @Test
  void duplicatePendingAndStaleMemberVersionsAreRejected() throws Exception {
    login(readerId, "READER", MemberType.READER);
    String body =
      "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"申请资格\"," +
      "\"acceptedRuleVersion\":\"BORROWING-1\",\"expectedMemberVersion\":0}";
    create(body);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isConflict());

    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"增加额度\"," +
              "\"requestedMaxBorrowCount\":7,\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void permanentQuotaApprovalClearsTemporaryQuota() throws Exception {
    jdbc.update(
      "update member set temporary_borrow_limit=9,temporary_quota_expires_at=? where id=?",
      java.time.LocalDateTime.now().plusDays(2),
      readerId
    );
    login(readerId, "READER", MemberType.READER);
    long id = create(
      "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"长期调整\",\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":0}"
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    approve(id);
    assertValue(
      "select permanent_borrow_limit from member where id=?",
      readerId,
      6
    );
    org.junit.jupiter.api.Assertions.assertNull(
      jdbc.queryForObject(
        "select temporary_borrow_limit from member where id=?",
        Integer.class,
        readerId
      )
    );
    org.junit.jupiter.api.Assertions.assertNull(
      jdbc.queryForObject(
        "select temporary_quota_expires_at from member where id=?",
        java.time.LocalDateTime.class,
        readerId
      )
    );
  }

  @Test
  void invalidTemporaryDurationIsRejected() throws Exception {
    login(readerId, "READER", MemberType.READER);
    for (int days : new int[] { 0, -1, 3651 }) {
      mvc
        .perform(
          post("/member-change-applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"无效天数\",\"requestedMaxBorrowCount\":7,\"quotaDurationDays\":" +
                days +
                ",\"expectedMemberVersion\":0}"
            )
        )
        .andExpect(status().isBadRequest());
    }
  }

  @Test
  void quotaCannotDropBelowBorrowedCountAndApprovedQuotaIsPersisted()
    throws Exception {
    jdbc.update("update member set borrowed_book_count=3 where id=?", readerId);
    login(readerId, "READER", MemberType.READER);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"降低额度\"," +
              "\"requestedMaxBorrowCount\":2,\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isConflict());

    long id = create(
      "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"临时增加额度\"," +
        "\"requestedMaxBorrowCount\":7,\"quotaDurationDays\":30,\"expectedMemberVersion\":0}"
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    approve(id);
    assertValue(
      "select permanent_borrow_limit from member where id=?",
      readerId,
      5
    );
    assertValue(
      "select temporary_borrow_limit from member where id=?",
      readerId,
      7
    );
    java.time.LocalDateTime expires = jdbc.queryForObject(
      "select temporary_quota_expires_at from member where id=?",
      java.time.LocalDateTime.class,
      readerId
    );
    org.junit.jupiter.api.Assertions.assertTrue(
      expires.isAfter(java.time.LocalDateTime.now().plusDays(29))
    );
  }

  @Test
  void employeeProfileChangeNeedsAdministratorAndCannotBeSelfApproved()
    throws Exception {
    Long newDepartment = createDepartment("NEW-DEPT", "新部门");
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long applicationId = create(
      "{\"applicationType\":\"EMPLOYEE_PROFILE_CHANGE\",\"reason\":\"岗位调整\"," +
        "\"requestedDepartmentId\":" +
        newDepartment +
        ",\"requestedPositionName\":\"流通主管\"," +
        "\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
    );

    mvc
      .perform(
        get("/admin/member-change-applications").param(
          "applicationType",
          "EMPLOYEE_PROFILE_CHANGE"
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isForbidden());

    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(applicationId);
    assertValue(
      "select department_id from employee_profile where member_id=?",
      librarianId,
      newDepartment
    );
    assertValue(
      "select position_name from employee_profile where member_id=?",
      librarianId,
      "流通主管"
    );
  }

  @Test
  void administratorCannotApproveOwnRequestButAnotherAdministratorCan()
    throws Exception {
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    long applicationId = create(
      "{\"applicationType\":\"EMPLOYEE_ROLE_CHANGE\",\"reason\":\"调整角色\"," +
        "\"requestedRoleId\":" +
        role("LIBRARIAN") +
        "," +
        "\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
    );
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isForbidden());

    login(secondAdminId, "ADMIN", MemberType.EMPLOYEE);
    approve(applicationId);
    assertValue(
      "select role_id from member where id=?",
      adminId,
      role("LIBRARIAN")
    );
  }

  @Test
  void staleApplicationCannotOverwriteNewerMemberOrProfileData()
    throws Exception {
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long applicationId = create(
      "{\"applicationType\":\"EMPLOYEE_PROFILE_CHANGE\",\"reason\":\"岗位调整\"," +
        "\"requestedPositionName\":\"新岗位\",\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
    );
    jdbc.update(
      "update employee_profile set version=version+1 where member_id=?",
      librarianId
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isConflict());
  }

  @Test
  void suspensionRevokesEmployeeRoleAndReinstatementRestoresApprovedRole()
    throws Exception {
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long suspension = create(
      "{\"applicationType\":\"EMPLOYEE_SUSPENSION\",\"reason\":\"申请停职\"," +
        "\"requestedEmploymentAction\":\"SUSPEND\",\"expectedMemberVersion\":0," +
        "\"expectedProfileVersion\":0}"
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(suspension);
    assertValue(
      "select employment_status from employee_profile where member_id=?",
      librarianId,
      "SUSPENDED"
    );
    assertValue(
      "select role_id from member where id=?",
      librarianId,
      role("READER")
    );

    // 停职后仍可用普通成员身份申请复职，但不能恢复员工权限，直到管理员批准。
    login(librarianId, "READER", MemberType.EMPLOYEE);
    long reinstatement = create(
      "{\"applicationType\":\"EMPLOYEE_REINSTATEMENT\",\"reason\":\"申请复职\"," +
        "\"requestedEmploymentAction\":\"REINSTATE\",\"requestedRoleId\":" +
        role("LIBRARIAN") +
        "," +
        "\"expectedMemberVersion\":1,\"expectedProfileVersion\":1}"
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(reinstatement);
    assertValue(
      "select employment_status from employee_profile where member_id=?",
      librarianId,
      "ACTIVE"
    );
    assertValue(
      "select role_id from member where id=?",
      librarianId,
      role("LIBRARIAN")
    );
  }

  @Test
  void disabledHumanAccountCanOnlyBeReactivatedByAdministratorApproval()
    throws Exception {
    jdbc.update(
      "update member set account_status='DISABLED' where id=?",
      readerId
    );
    // 当前仅验证业务闭环；正式入口将由邮箱验证码建立受限恢复会话。
    login(readerId, "READER", MemberType.READER);
    long applicationId = create(
      "{\"applicationType\":\"ACCOUNT_REACTIVATION\",\"reason\":\"申请恢复账号\"," +
        "\"requestedAccountAction\":\"REACTIVATE\",\"expectedMemberVersion\":0}"
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isForbidden());

    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(applicationId);
    assertValue(
      "select account_status from member where id=?",
      readerId,
      "ACTIVE"
    );
  }

  @Test
  void aggregateCreateDtoRejectsFieldsOutsideSelectedApplicationType()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"夹带字段\"," +
              "\"acceptedRuleVersion\":\"BORROWING-1\",\"requestedRoleId\":1," +
              "\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.msg").value("申请包含当前类型不允许修改的字段"));
  }

  @Test
  void aiCannotSubmitOrReviewApplications() throws Exception {
    login(999L, "AI_LIBRARIAN", MemberType.AI_LIBRARIAN);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"READER_QUALIFICATION\",\"reason\":\"越权\"," +
              "\"acceptedRuleVersion\":\"BORROWING-1\",\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(get("/admin/member-change-applications"))
      .andExpect(status().isForbidden());
  }

  @Test
  void aggregateQuerySupportsCombinedFieldsAndRejectsInvalidRanges()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    long applicationId = create(
      "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"组合查询\"," +
        "\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":0}"
    );
    String applicationNo = jdbc.queryForObject(
      "select application_no from member_change_application where id=?",
      String.class,
      applicationId
    );

    mvc
      .perform(
        get("/member-change-applications")
          .param("id", String.valueOf(applicationId))
          .param("applicationNo", applicationNo)
          .param("applicantMemberId", String.valueOf(secondReaderId))
          .param("targetMemberId", String.valueOf(secondReaderId))
          .param("applicantMemberType", "READER")
          .param("applicationType", "BORROW_QUOTA_CHANGE")
          .param("status", "PENDING")
          .param("page", "1")
          .param("pageSize", "1")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].id").value(applicationId))
      .andExpect(jsonPath("$.data.rows[0].applicantMemberId").value(readerId));

    mvc
      .perform(
        get("/member-change-applications")
          .param("createdFrom", "2026-10-02T00:00:00")
          .param("createdTo", "2026-10-01T00:00:00")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/member-change-applications").param("id", "0"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void anotherReaderCannotCancelSomeoneElsesApplication() throws Exception {
    login(readerId, "READER", MemberType.READER);
    long applicationId = create(
      "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"本人申请\"," +
        "\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":0}"
    );

    login(secondReaderId, "READER", MemberType.READER);
    mvc
      .perform(
        post("/member-change-applications/{id}/cancel", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"expectedVersion\":0}")
      )
      .andExpect(status().isForbidden());
    assertValue(
      "select status from member_change_application where id=?",
      applicationId,
      "PENDING"
    );
  }

  @Test
  void noOpAndInactiveTargetsAreRejectedBeforeCreatingApplication()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"相同额度\"," +
              "\"requestedMaxBorrowCount\":5,\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isConflict());

    Long departmentId = jdbc.queryForObject(
      "select department_id from employee_profile where member_id=?",
      Long.class,
      librarianId
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"EMPLOYEE_PROFILE_CHANGE\",\"reason\":\"没有变化\"," +
              "\"requestedDepartmentId\":" +
              departmentId +
              ",\"requestedPositionName\":\"馆员\"," +
              "\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
          )
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"EMPLOYEE_ROLE_CHANGE\",\"reason\":\"相同角色\"," +
              "\"requestedRoleId\":" +
              role("LIBRARIAN") +
              "," +
              "\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
          )
      )
      .andExpect(status().isConflict());

    Long disabled = createDepartment("DISABLED-DEPT", "停用部门");
    jdbc.update("update department set status='DISABLED' where id=?", disabled);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"EMPLOYEE_TRANSFER\",\"reason\":\"调入停用部门\"," +
              "\"requestedDepartmentId\":" +
              disabled +
              ",\"requestedEmploymentAction\":\"TRANSFER\"," +
              "\"expectedMemberVersion\":0,\"expectedProfileVersion\":0}"
          )
      )
      .andExpect(status().isConflict());
  }

  @Test
  void selfScopedReviewPermissionCannotReadOrApproveAllApplications()
    throws Exception {
    login(readerId, "READER", MemberType.READER);
    long applicationId = create(
      "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"范围测试\"," +
        "\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":0}"
    );
    jdbc.update(
      "update role_permission set data_scope='SELF' where role_id=? and permission_id=" +
        "(select id from permission where code='member-change:query:reader')",
      role("LIBRARIAN")
    );

    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    mvc
      .perform(get("/admin/member-change-applications"))
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}")
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void qualificationRestoreTransferResignationAndLeftReinstatementWork()
    throws Exception {
    jdbc.update(
      "update member set qualification_status='SUSPENDED',accepted_rule_version='BORROWING-1' where id=?",
      readerId
    );
    login(readerId, "READER", MemberType.READER);
    long restore = create(
      "{\"applicationType\":\"READER_QUALIFICATION_RESTORE\",\"reason\":\"恢复资格\"," +
        "\"expectedMemberVersion\":0}"
    );
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    approve(restore);
    assertValue(
      "select qualification_status from member where id=?",
      readerId,
      "APPROVED"
    );

    Long newDepartment = createDepartment("TRANSFER-DEPT", "调入部门");
    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long transfer = create(
      "{\"applicationType\":\"EMPLOYEE_TRANSFER\",\"reason\":\"申请调岗\"," +
        "\"requestedDepartmentId\":" +
        newDepartment +
        "," +
        "\"requestedEmploymentAction\":\"TRANSFER\",\"expectedMemberVersion\":0," +
        "\"expectedProfileVersion\":0}"
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(transfer);
    assertValue(
      "select department_id from employee_profile where member_id=?",
      librarianId,
      newDepartment
    );

    login(librarianId, "LIBRARIAN", MemberType.EMPLOYEE);
    long resign = create(
      "{\"applicationType\":\"EMPLOYEE_RESIGNATION\",\"reason\":\"申请离职\"," +
        "\"requestedEmploymentAction\":\"RESIGN\",\"expectedMemberVersion\":0," +
        "\"expectedProfileVersion\":1}"
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(resign);
    assertValue(
      "select employment_status from employee_profile where member_id=?",
      librarianId,
      "LEFT"
    );
    assertValue(
      "select role_id from member where id=?",
      librarianId,
      role("READER")
    );

    login(librarianId, "READER", MemberType.EMPLOYEE);
    long reinstate = create(
      "{\"applicationType\":\"EMPLOYEE_REINSTATEMENT\",\"reason\":\"离职后复职\"," +
        "\"requestedEmploymentAction\":\"REINSTATE\",\"requestedRoleId\":" +
        role("LIBRARIAN") +
        "," +
        "\"expectedMemberVersion\":1,\"expectedProfileVersion\":2}"
    );
    login(adminId, "ADMIN", MemberType.EMPLOYEE);
    approve(reinstate);
    assertValue(
      "select employment_status from employee_profile where member_id=?",
      librarianId,
      "ACTIVE"
    );
    assertValue(
      "select role_id from member where id=?",
      librarianId,
      role("LIBRARIAN")
    );
  }

  @Test
  void oversizedAndNegativeFieldsReturnBadRequest() throws Exception {
    login(readerId, "READER", MemberType.READER);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"测试\"," +
              "\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":-1}"
          )
      )
      .andExpect(status().isBadRequest());

    String longReason = "a".repeat(501);
    mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"applicationType\":\"BORROW_QUOTA_CHANGE\",\"reason\":\"" +
              longReason +
              "\"," +
              "\"requestedMaxBorrowCount\":6,\"expectedMemberVersion\":0}"
          )
      )
      .andExpect(status().isBadRequest());
  }

  private long create(String body) throws Exception {
    String response = mvc
      .perform(
        post("/member-change-applications")
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String marker = "\"id\":";
    int start = response.indexOf(marker) + marker.length();
    int end = response.indexOf(',', start);
    return Long.parseLong(response.substring(start, end));
  }

  private void approve(long applicationId) throws Exception {
    mvc
      .perform(
        post("/admin/member-change-applications/{id}/decision", applicationId)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"decision\":\"APPROVE\",\"reason\":\"同意\",\"expectedVersion\":0}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("APPROVED"));
  }

  private Long insertMember(
    String memberType,
    String roleCode,
    String name,
    String email,
    String qualification,
    int borrowed,
    int maximum
  ) {
    jdbc.update(
      "insert into member(member_type,role_id,name,email,phone,password_hash,account_status," +
        "qualification_status,borrowed_book_count,permanent_borrow_limit,version) " +
        "values(?,(select id from `role` where code=?),?,?,?,'hash','ACTIVE',?,?,?,0)",
      memberType,
      roleCode,
      name,
      email,
      "139" + Math.abs(email.hashCode()),
      qualification,
      borrowed,
      maximum
    );
    return jdbc.queryForObject(
      "select id from member where email=?",
      Long.class,
      email
    );
  }

  private void insertEmployee(
    Long memberId,
    String no,
    Long departmentId,
    String position
  ) {
    jdbc.update(
      "insert into employee_profile(member_id,employee_no,department_id,position_name," +
        "employment_status,hired_on,version) values(?,?,?,?,'ACTIVE',current_date,0)",
      memberId,
      no,
      departmentId,
      position
    );
  }

  private Long createDepartment(String code, String name) {
    jdbc.update(
      "insert into department(code,name,status,version) values(?,?,'ACTIVE',0)",
      code,
      name
    );
    return jdbc.queryForObject(
      "select id from department where code=?",
      Long.class,
      code
    );
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
  }

  private void login(Long memberId, String roleCode, MemberType type) {
    LoginUserContext.set(
      new LoginSession(
        "change-test",
        memberId,
        role(roleCode),
        type,
        roleCode,
        "test@example.test",
        "测试用户"
      )
    );
  }

  private void assertValue(String sql, Long id, Object expected) {
    Object actual = jdbc.queryForObject(sql, Object.class, id);
    org.assertj.core.api.Assertions.assertThat(
      String.valueOf(actual)
    ).isEqualTo(String.valueOf(expected));
  }
}
