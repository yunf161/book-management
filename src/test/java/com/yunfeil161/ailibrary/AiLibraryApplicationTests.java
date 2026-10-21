package com.yunfeil161.ailibrary;

import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AiLibraryApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PermissionQueryService permissionQueryService;

    @Autowired
    private CacheClient cacheClient;

    /** 管理接口测试显式建立管理员上下文，验证与生产相同的 roleId 鉴权路径。 */
    @BeforeEach
    void loginAsAdministrator() {
        LoginUserContext.set(new LoginSession(
                "identity-test", 1L, roleId("ADMIN"), MemberType.EMPLOYEE,
                "ADMIN", "admin@example.test", "测试管理员"));
    }

    @AfterEach
    void clearLoginContext() {
        LoginUserContext.clear();
    }

    @Test
    void retiredAiMemberCrudIsRejectedWithoutChangingMembers() throws Exception {
        Integer before = jdbcTemplate.queryForObject("select count(*) from member", Integer.class);
        mockMvc.perform(get("/admin/members").param("memberType", "AI_LIBRARIAN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberType\":\"AI_LIBRARIAN\",\"name\":\"退役服务身份\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/admin/members/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberType\":\"AI_LIBRARIAN\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/admin/members/999999").param("memberType", "AI_LIBRARIAN"))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(before,
                jdbcTemplate.queryForObject("select count(*) from member", Integer.class));
    }

    @Test
    void departmentApiLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String departmentCode = "IT" + suffix;
        String departmentName = "MySQL Integration " + suffix;
        String employeeNo = "IT" + suffix;
        Long departmentId = null;
        String department = """
                {"code":"%s","name":"%s","description":"Created by integration test","status":"ACTIVE"}
                """.formatted(departmentCode, departmentName);

        try {
            mockMvc.perform(post("/admin/departments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(department))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            departmentId = jdbcTemplate.queryForObject(
                    "select id from department where code = ?", Long.class, departmentCode);

            mockMvc.perform(post("/admin/departments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(department))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));

            mockMvc.perform(get("/admin/departments")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rows[0].code").value(departmentCode))
                    .andExpect(jsonPath("$.data.rows[0].status").value("ACTIVE"));

            mockMvc.perform(get("/admin/departments")
                            .param("name", suffix)
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rows[0].id").value(departmentId));

            mockMvc.perform(get("/admin/departments")
                            .param("id", String.valueOf(departmentId))
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.rows[0].id").value(departmentId));

            mockMvc.perform(put("/admin/departments/{id}", departmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"CANNOT_CHANGE\",\"name\":\"Updated Department\",\"status\":\"DISABLED\",\"expectedVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.code").value(departmentCode))
                    .andExpect(jsonPath("$.data.description").value("Created by integration test"))
                    .andExpect(jsonPath("$.data.version").value(1));

            mockMvc.perform(put("/admin/departments/{id}", departmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":null,\"expectedVersion\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.description").doesNotExist())
                    .andExpect(jsonPath("$.data.version").value(2));

            mockMvc.perform(put("/admin/departments/{id}", departmentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Expired Version Department\",\"expectedVersion\":1}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));

            jdbcTemplate.update("insert into employee_profile (member_id, employee_no, department_id, position_name, employment_status, hired_on) values (?, ?, ?, ?, ?, ?)",
                    System.currentTimeMillis(), employeeNo, departmentId, "Integration Test", "ACTIVE", LocalDate.now());
            mockMvc.perform(delete("/admin/departments/{id}", departmentId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));

            jdbcTemplate.update("delete from employee_profile where department_id = ?", departmentId);
            mockMvc.perform(delete("/admin/departments/{id}", departmentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/admin/departments")
                            .param("id", String.valueOf(departmentId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        } finally {
            if (departmentId != null) {
                jdbcTemplate.update("delete from employee_profile where department_id = ?", departmentId);
                jdbcTemplate.update("delete from department where id = ?", departmentId);
            }
        }
    }

    @Test
    void permissionApiVersionLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String permissionCode = "TEST_PERMISSION_" + suffix;
        Long permissionId = null;
        String permission = """
                {"code":"%s","name":"Test Permission","module":"IDENTITY","description":"Created by integration test"}
                """.formatted(permissionCode);

        try {
            mockMvc.perform(post("/admin/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(permission))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            permissionId = jdbcTemplate.queryForObject(
                    "select id from permission where code = ?", Long.class, permissionCode);

            mockMvc.perform(put("/admin/permissions/{id}", permissionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Updated Permission\",\"module\":\"IDENTITY\",\"description\":\"Updated\",\"expectedVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(1))
                    .andExpect(jsonPath("$.data.name").value("Updated Permission"));

            mockMvc.perform(put("/admin/permissions/{id}", permissionId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Stale Update\",\"module\":\"IDENTITY\",\"description\":\"Stale\",\"expectedVersion\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));

            Long adminRoleId = roleId("ADMIN");
            jdbcTemplate.update(
                    "insert into role_permission (role_id, permission_id, data_scope) values (?, ?, ?)",
                    adminRoleId, permissionId, "ALL");
            mockMvc.perform(delete("/admin/permissions/{id}", permissionId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
            jdbcTemplate.update("delete from role_permission where permission_id = ?", permissionId);

            mockMvc.perform(delete("/admin/permissions/{id}", permissionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            if (permissionId != null) {
                jdbcTemplate.update("delete from permission where id = ?", permissionId);
            }
        }
    }

    @Test
    void memberQuerySeparatesMemberTypes() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Long readerId = null;
        Long employeeId = null;
        Long adminId = null;
        Long secondAdminId = null;
        Long aiId = null;
        try {
            readerId = insertMember("Reader " + suffix, 1);
            employeeId = insertMember("Employee " + suffix, 2);
            adminId = insertMember("Admin " + suffix, 3);
            secondAdminId = insertMember("Additional Admin " + adminId, 3);
            aiId = insertMember("AI " + suffix, 4);
            jdbcTemplate.update("insert into reader_interest (member_id, interest_name) values (?, ?)",
                    readerId, "Interest" + suffix);
            jdbcTemplate.update("insert into reader_settings (member_id, personalization_enabled, recommendation_notifications) values (?, ?, ?)",
                    readerId, true, true);
            jdbcTemplate.update("insert into employee_profile (member_id, employee_no, department_id, position_name, employment_status, hired_on) values (?, ?, ?, ?, ?, ?)",
                    employeeId, "EMP" + suffix, 1, "Librarian", "ACTIVE", LocalDate.now());
            jdbcTemplate.update("insert into employee_profile (member_id, employee_no, department_id, position_name, employment_status, hired_on) values (?, ?, ?, ?, ?, ?)",
                    adminId, "ADM" + suffix, 1, "Administrator", "ACTIVE", LocalDate.now());
            jdbcTemplate.update("insert into employee_profile (member_id, employee_no, department_id, position_name, employment_status, hired_on) values (?, ?, ?, ?, ?, ?)",
                    secondAdminId, "ADM2" + suffix, 1, "Administrator", "ACTIVE", LocalDate.now());

            assertMemberType(suffix, "READER", readerId, "Reader " + suffix);
            assertMemberType(suffix, "EMPLOYEE", employeeId, "Employee " + suffix);
            assertMemberType(suffix, "EMPLOYEE", adminId, "Admin " + suffix);
            // 旧类型可用于历史拒绝访问测试，但人员维护不再提供 AI 查询分支。

            mockMvc.perform(get("/admin/members")
                            .param("memberType", "READER")
                            .param("interestName", suffix)
                            .param("page", "1")
                    .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.rows[0].name").value("Reader " + suffix));
            mockMvc.perform(get("/admin/members")
                            .param("memberType", "EMPLOYEE")
                            .param("employeeNo", "EMP" + suffix)
                            .param("page", "1")
                    .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.rows[0].employeeNo").value("EMP" + suffix));
            mockMvc.perform(get("/admin/members")
                            .param("memberType", "AI_LIBRARIAN")
                            .param("serviceCode", "AI_" + suffix)
                            .param("page", "1")
                    .param("pageSize", "10"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        } finally {
            if (readerId != null) jdbcTemplate.update("delete from reader_interest where member_id = ?", readerId);
            if (readerId != null) jdbcTemplate.update("delete from reader_settings where member_id = ?", readerId);
            if (employeeId != null) jdbcTemplate.update("delete from employee_profile where member_id = ?", employeeId);
            if (adminId != null) jdbcTemplate.update("delete from employee_profile where member_id = ?", adminId);
            if (secondAdminId != null) jdbcTemplate.update("delete from employee_profile where member_id = ?", secondAdminId);
            jdbcTemplate.update("delete from member where id in (?, ?, ?, ?, ?)",
                    readerId, employeeId, adminId, secondAdminId, aiId);
        }
    }

    @Test
    void rolePermissionAndDefaultRoleBehavior() throws Exception {
        Long adminRoleId = roleId("ADMIN");
        Long readerRoleId = roleId("READER");

        org.junit.jupiter.api.Assertions.assertTrue(
                permissionQueryService.hasPermission(adminRoleId, "department:delete"));
        org.junit.jupiter.api.Assertions.assertFalse(
                permissionQueryService.hasPermission(readerRoleId, "department:delete"));
        org.junit.jupiter.api.Assertions.assertEquals(
                DataScope.NONE,
                permissionQueryService.findDataScope(adminRoleId, "department:delete").orElseThrow());
        org.junit.jupiter.api.Assertions.assertTrue(
                permissionQueryService.findDataScope(readerRoleId, "department:delete").isEmpty());

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String name = "Default Role " + suffix;
        Long memberId = null;
        Long adminMemberId = null;
        Long adminDepartmentId = null;
        try {
            String request = """
                    {"memberType":"READER","name":"%s","email":"%s@example.test",
                     "phone":"%s","passwordHash":"hash","interests":["Java"]}
                    """.formatted(name, suffix.toLowerCase(), suffix);
            mockMvc.perform(post("/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roleId").value(readerRoleId));
            memberId = jdbcTemplate.queryForObject(
                    "select id from member where name = ?", Long.class, name);
            String assignedRoleCode = jdbcTemplate.queryForObject(
                    "select r.code from member m join `role` r on r.id = m.role_id where m.id = ?",
                    String.class, memberId);
            org.junit.jupiter.api.Assertions.assertEquals("READER", assignedRoleCode);

            mockMvc.perform(post("/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberType\":\"EMPLOYEE\",\"roleId\":" + adminRoleId
                                    + ",\"name\":\"Blocked Admin\","
                                    + "\"email\":\"blocked.admin@example.test\",\"phone\":\"10086\","
                                    + "\"passwordHash\":\"hash\"}"))
                    .andExpect(status().isBadRequest());
            String adminName = "Existing Admin " + suffix;
            String departmentCode = "ADMIN_TEST_" + suffix;
            jdbcTemplate.update("""
                    insert into department (code, name, status)
                    values (?, ?, 'ACTIVE')
                    """, departmentCode, "Admin Test Department " + suffix);
            adminDepartmentId = jdbcTemplate.queryForObject(
                    "select id from department where code = ?", Long.class, departmentCode);
            jdbcTemplate.update("""
                    insert into member
                        (name, member_type, role_id, account_status, qualification_status, version)
                    values (?, 'EMPLOYEE', ?, 'ACTIVE', 'NOT_APPLIED', 0)
                    """, adminName, adminRoleId);
            adminMemberId = jdbcTemplate.queryForObject(
                    "select id from member where name = ?", Long.class, adminName);
            jdbcTemplate.update("""
                    insert into employee_profile
                        (member_id, employee_no, department_id, position_name,
                         employment_status, hired_on)
                    values (?, ?, ?, '系统管理员', 'ACTIVE', current_date)
                    """, adminMemberId, "ADMIN-" + suffix, adminDepartmentId);

            mockMvc.perform(put("/members/{id}", adminMemberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberType\":\"EMPLOYEE\",\"roleId\":" + adminRoleId
                                    + ",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/members/{id}", adminMemberId)
                            .param("memberType", "EMPLOYEE"))
                    .andExpect(status().isBadRequest());
        } finally {
            if (adminMemberId != null) {
                jdbcTemplate.update("delete from employee_profile where member_id = ?", adminMemberId);
                jdbcTemplate.update("delete from member where id = ?", adminMemberId);
            }
            if (adminDepartmentId != null) {
                jdbcTemplate.update("delete from department where id = ?", adminDepartmentId);
            }
            if (memberId != null) {
                jdbcTemplate.update("delete from reader_interest where member_id = ?", memberId);
                jdbcTemplate.update("delete from reader_settings where member_id = ?", memberId);
                jdbcTemplate.update("delete from member where id = ?", memberId);
            }
        }
    }

    private Long insertMember(String name, int memberType) {
        jdbcTemplate.update("insert into member (name, member_type, role_id, account_status, qualification_status) values (?, ?, ?, ?, ?)",
                name, com.yunfeil161.ailibrary.testsupport.TestMemberTypes.fromFixtureCode(memberType).name(),
                roleIdForLevel(memberType), "ACTIVE", "NOT_APPLIED");
        Long id = jdbcTemplate.queryForObject("select id from member where name = ?", Long.class, name);
        // H2 每次测试进程会复用较小的自增 ID，必须清除 Redis 中上次进程留下的同 ID 对象。
        cacheClient.delete("member:reader:" + id);
        cacheClient.delete("member:employee:" + id);
        cacheClient.delete("member:ai-librarian:" + id);
        return id;
    }

    private void assertMemberType(String suffix, String memberType, Long id, String name) throws Exception {
        mockMvc.perform(get("/admin/members")
                        .param("memberType", memberType)
                        .param("id", String.valueOf(id))
                        .param("name", suffix)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].name").value(name));
    }

    private Long roleIdForLevel(int memberType) {
        return roleId(switch (memberType) {
            case 1 -> "READER";
            case 2 -> "LIBRARIAN";
            case 3 -> "ADMIN";
            case 4 -> "AI_LIBRARIAN";
            default -> throw new IllegalArgumentException("Unsupported permission level: " + memberType);
        });
    }

    private Long roleId(String code) {
        return jdbcTemplate.queryForObject(
                "select id from `role` where code = ?", Long.class, code);
    }

}

