package com.yunfeil161.ailibrary.communication.contact.dto;

import com.yunfeil161.ailibrary.common.enums.MemberType;
import lombok.Data;

/** 联系人公开摘要，不包含邮箱、电话、密码或管理资料。 */
@Data
public class ContactBrief {
  private Long id;
  private String name;
  private MemberType memberType;
  private String departmentName;
}
