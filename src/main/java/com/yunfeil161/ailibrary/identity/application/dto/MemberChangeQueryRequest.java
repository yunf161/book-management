package com.yunfeil161.ailibrary.identity.application.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人和管理端共用的全字段组合查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberChangeQueryRequest extends PageQuery {
  private Long id;
  private String applicationNo;
  private Long applicantMemberId;
  private Long targetMemberId;
  private MemberType applicantMemberType;
  private MemberChangeType applicationType;
  private MemberChangeStatus status;
  private Long reviewerMemberId;
  private LocalDateTime createdFrom;
  private LocalDateTime createdTo;
}
