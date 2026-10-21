package com.yunfeil161.ailibrary.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.identity.dto.MemberWriteRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 验证 SELF 范围不能借普通资料接口修改角色、账号和任职字段。 */
class MemberSelfUpdateTests {

  @Test
  void retainSelfEditableFieldsRemovesManagementFields() {
    MemberWriteRequest request = new MemberWriteRequest();
    request.setMemberType(MemberType.EMPLOYEE);
    request.setExpectedVersion(4L);
    request.setName("新姓名");
    request.setPhone("13800000000");
    request.setRoleId(99L);
    request.setEmail("changed@example.test");
    request.setPasswordHash("unsafe");
    request.setAccountStatus(AccountStatus.DISABLED);
    request.setQualificationStatus(QualificationStatus.APPROVED);
    request.setPermanentBorrowLimit(999);
    request.setEmployeeNo("FORGED");
    request.setDepartmentId(88L);
    request.setPositionName("管理员");
    request.setEmploymentStatus(EmploymentStatus.LEFT);
    request.setHiredOn(LocalDate.now());

    request.retainSelfEditableFields();

    assertEquals(MemberType.EMPLOYEE, request.getMemberType());
    assertEquals(4L, request.getExpectedVersion());
    assertEquals("新姓名", request.getName());
    assertEquals("13800000000", request.getPhone());
    assertNull(request.getRoleId());
    assertNull(request.getEmail());
    assertNull(request.getPasswordHash());
    assertNull(request.getAccountStatus());
    assertNull(request.getQualificationStatus());
    assertNull(request.getPermanentBorrowLimit());
    assertNull(request.getEmployeeNo());
    assertNull(request.getDepartmentId());
    assertNull(request.getPositionName());
    assertNull(request.getEmploymentStatus());
    assertNull(request.getHiredOn());
  }
}
