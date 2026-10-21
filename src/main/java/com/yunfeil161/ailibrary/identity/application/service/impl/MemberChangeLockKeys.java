package com.yunfeil161.ailibrary.identity.application.service.impl;

import com.yunfeil161.ailibrary.identity.application.mapper.MemberChangeApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 审批、取消和自动过期共用资源键；先解析不可变成员ID，再按统一顺序加锁。 */
@Component("memberChangeLockKeys")
@RequiredArgsConstructor
public class MemberChangeLockKeys {

  private final MemberChangeApplicationMapper applications;

  public String[] forApplication(Long id) {
    var application = id == null ? null : applications.findById(id);
    return new String[] {
      "lock:resource:policy-current:ADMISSION",
      "lock:resource:policy-current:BORROWING",
      "lock:resource:member:" +
        (application == null ? "missing" : application.getTargetMemberId()),
      "lock:resource:member-change-application:" + id,
    };
  }
}
