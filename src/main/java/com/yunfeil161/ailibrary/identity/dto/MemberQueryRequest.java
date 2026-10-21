package com.yunfeil161.ailibrary.identity.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

/**
 * 成员分页查询参数。
 *
 * <p>统一接收成员公共字段、读者字段、员工字段。
 * Controller 根据 memberType 将其转换为对应的领域对象。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberQueryRequest extends PageQuery {

  /** CONTACT返回通信联系人摘要；为空时保持原人员查询规则。 */
  private String scene;

  // member 公共字段
  private Long id;
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
  private Integer temporaryBorrowLimit;
  private LocalDateTime temporaryQuotaExpiresAt;
  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // reader 专属字段
  private Long readerInterestId;
  private String interestName;
  private String interests;
  private Long readerSettingsId;
  private Boolean personalizationEnabled;
  private Boolean recommendationNotificationsEnabled;

  // employee 专属字段
  private Long employeeProfileId;
  private String employeeNo;
  private Long departmentId;
  private String positionName;
  private EmploymentStatus employmentStatus;
  private LocalDate hiredOn;
  private LocalDate leftOn;

  /** 将当前参数转换为读者查询对象。 */
  public Reader toReader() {
    Reader reader = new Reader();
    BeanUtils.copyProperties(this, reader);
    return reader;
  }

  /** 将当前参数转换为员工查询对象。 */
  public Employee toEmployee() {
    Employee employee = new Employee();
    BeanUtils.copyProperties(this, employee);
    return employee;
  }
}
