package com.yunfeil161.ailibrary.communication.common.security;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.enums.*;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 操作授权与成员可用性检查，不根据身份数字推断权限。 */
@Component
@RequiredArgsConstructor
public class CommunicationAccess {
  private final PermissionAccess permissions;
  private final MemberMapper members;
  private final com.yunfeil161.ailibrary.communication.contact.service.ContactService contacts;

  public LoginSession require(String code) {
    LoginSession session = permissions.require(code);
    active(session.getMemberId());
    return session;
  }

  public Member active(Long id) {
    if (id == null || id < 1) throw new WrongFormatException("成员ID必须大于0");
    Member member = members.findMemberById(id);
    if (member == null) throw new NotFoundException("成员不存在");
    if (member.getAccountStatus() != AccountStatus.ACTIVE
        || member.getMemberType() == MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("成员停用或不支持通信");
    }
    return member;
  }

  public void pair(Long sender, Long recipient) {
    if (sender.equals(recipient)) throw new WrongFormatException("不能向自己发送消息");
    Member a = active(sender);
    Member b = active(recipient);
    contacts.requireTarget(sender, recipient);
    if (a.getMemberType() != MemberType.EMPLOYEE && b.getMemberType() != MemberType.EMPLOYEE) {
      throw new ForbiddenException("读者只能与馆员通信");
    }
  }
}
