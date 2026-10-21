package com.yunfeil161.ailibrary.identity.dto;

import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/** 新增、更新成员时使用的统一请求对象。 */
@Data
public class MemberWriteRequest {

  private MemberType memberType;
  private Long roleId;
  private String name;
  private String email;
  private String phone;
  private String passwordHash;
  private AccountStatus accountStatus;
  private QualificationStatus qualificationStatus;
  private LocalDateTime emailVerifiedAt;
  private String acceptedRuleVersion;
  private Integer borrowedBookCount;
  private Integer permanentBorrowLimit;
  /** 查询展示字段；普通资料维护不能绕过申请审批直接写入临时额度。 */
  private Integer temporaryBorrowLimit;
  private LocalDateTime temporaryQuotaExpiresAt;

  private List<String> interests;
  private Boolean personalizationEnabled;
  private Boolean recommendationNotificationsEnabled;

  private String employeeNo;
  private Long departmentId;
  private String positionName;
  private EmploymentStatus employmentStatus;
  private LocalDate hiredOn;
  private LocalDate leftOn;

  /** 更新时必填，值取自查询结果中的 version。 */
  private Long expectedVersion;

  public Reader toReader() {
    Reader value = new Reader();
    BeanUtils.copyProperties(this, value);
    return value;
  }

  public Employee toEmployee() {
    Employee value = new Employee();
    BeanUtils.copyProperties(this, value);
    return value;
  }

  /**
   * 本人资料接口只保留无需审批的普通资料字段。
   *
   * <p>角色、账号状态、借阅资格和额度、员工任职信息属于管理字段，
   * 即使客户端主动提交也不能通过 SELF 数据范围修改。密码使用独立的验证码
   * 重置流程，邮箱验证状态也只能由认证流程维护。</p>
   */
  public void retainSelfEditableFields() {
    roleId = null;
    email = null;
    passwordHash = null;
    accountStatus = null;
    qualificationStatus = null;
    emailVerifiedAt = null;
    borrowedBookCount = null;
    permanentBorrowLimit = null;
    temporaryBorrowLimit = null;
    temporaryQuotaExpiresAt = null;

    employeeNo = null;
    departmentId = null;
    positionName = null;
    employmentStatus = null;
    hiredOn = null;
    leftOn = null;
  }
}
