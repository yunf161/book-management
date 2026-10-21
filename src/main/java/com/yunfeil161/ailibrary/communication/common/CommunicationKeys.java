package com.yunfeil161.ailibrary.communication.common;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import org.springframework.stereotype.Component;

/** 参与者固定排序，让A创建与B创建同一会话取得同一把可重入锁。 */
@Component("communicationKeys")
public class CommunicationKeys {
  public String conversation(Long targetMemberId) {
    Long me = LoginUserContext.get().getMemberId();
    if (targetMemberId == null || targetMemberId <= 0 || me.equals(targetMemberId)) {
      throw new WrongFormatException("目标成员ID不合法");
    }
    return "communication:pair:"
        + Math.min(me, targetMemberId)
        + ":"
        + Math.max(me, targetMemberId);
  }
}
