package com.yunfeil161.ailibrary.identity.entity;

import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Member {

  private Long id;
  private MemberType memberType;
  private Long roleId;
  private String name;
  private String email;
  private String phone;

  /** 密码摘要仅供内部认证与持久化，任何HTTP/缓存JSON输出都不携带该字段。 */
  @com.fasterxml.jackson.annotation.JsonProperty(
    access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY
  )
  private String passwordHash;

  private AccountStatus accountStatus;
  private QualificationStatus qualificationStatus; //借阅资格
  private LocalDateTime emailVerifiedAt;
  private String acceptedRuleVersion;
  /** 当前已经借出的图书数量。 */
  private Integer borrowedBookCount;
  /** 长期借阅总额度，临时额度到期后重新使用此值。 */
  private Integer permanentBorrowLimit;
  /** 临时期间的总额度，不是叠加数量；只能通过额度申请审批写入。 */
  private Integer temporaryBorrowLimit;
  /** 临时额度的失效时间，到达该时间即使用长期额度。 */
  private LocalDateTime temporaryQuotaExpiresAt;

  /** 每次响应动态计算，不能把计算结果缓存，否则到期后仍会显示旧额度。 */
  @com.fasterxml.jackson.annotation.JsonProperty(
    access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY
  )
  public Integer getEffectiveBorrowLimit() {
    return effectiveBorrowLimitAt(LocalDateTime.now());
  }

  /** 可指定业务时间，便于验证到期边界；到期后仍允许归还已有借阅。 */
  public Integer effectiveBorrowLimitAt(LocalDateTime now) {
    return temporaryBorrowLimit != null &&
      temporaryQuotaExpiresAt != null &&
      now.isBefore(temporaryQuotaExpiresAt)
      ? temporaryBorrowLimit
      : permanentBorrowLimit;
  }

  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
