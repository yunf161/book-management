package com.yunfeil161.ailibrary.identity.application.dto;

import lombok.Data;

/** 取消本人待处理申请时提交其当前版本。 */
@Data
public class MemberChangeCancelRequest {
  private Long expectedVersion;
}
