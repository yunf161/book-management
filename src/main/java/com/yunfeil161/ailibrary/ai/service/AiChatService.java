package com.yunfeil161.ailibrary.ai.service;

import com.yunfeil161.ailibrary.ai.business.BusinessActionService;
import com.yunfeil161.ailibrary.ai.dto.AiChatRequest;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.config.security.permission.*;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 人类成员的咨询/业务规划入口。不在模型推理期间占用业务事务或资源锁。 */
@Service
@RequiredArgsConstructor
public class AiChatService {

  private final PermissionAccess access;
  private final AiGatewayClient gateway;
  private final BusinessActionService business;
  private final com.yunfeil161.ailibrary.ai.runtime.AiRuntimeControl controls;
  private final com.yunfeil161.ailibrary.ai.handoff.AiHandoffMapper handoffs;

  public LoginSession authorize() {
    var session = access.require(PermissionCodes.AI_CHAT_SELF, DataScope.SELF);
    if (
      session.getMemberType() == MemberType.AI_LIBRARIAN
    ) throw new ForbiddenException("AI馆员不使用人类咨询入口");
    return session;
  }

  public AiGatewayClient.Stream chat(AiChatRequest input, String token) {
    var session = authorize();
    if (
      input == null ||
      input.question() == null ||
      input.question().isBlank() ||
      input.question().length() > 2000
    ) {
      throw new WrongFormatException("问题须为1到2000字符");
    }
    if (input.conversationId() != null) {
      validateId(input.conversationId());
      requireAutomated(input.conversationId());
    }
    if (
      input.task() != null &&
      !java.util.Set.of("CONSULTATION", "BUSINESS").contains(input.task())
    ) throw new WrongFormatException("智能体类型错误");
    if (
      token == null ||
      token.isBlank() ||
      token.length() > 8192 ||
      token.contains("\n") ||
      token.contains("\r")
    ) {
      throw new UnauthorizedException("缺少有效业务令牌");
    }
    if ("BUSINESS".equals(input.task())) {
      controls.requireEnabled("BUSINESS");
      business.authorize();
      return gateway.business(
        session.getMemberId(),
        token,
        input.question().strip(),
        input.conversationId()
      );
    }
    controls.requireEnabled("CONSULTATION");
    return gateway.chat(
      session.getMemberId(),
      token,
      input.question().strip(),
      input.conversationId()
    );
  }

  public Object conversation(String id, boolean delete) {
    var session = authorize();
    validateId(id);
    return gateway.conversation(session.getMemberId(), id, delete);
  }

  /** 馆员接管后当前AI会话停答；人工消息不作为模型上下文继续外发。 */
  public void requireAutomated(String id) {
    if (
      id != null &&
      handoffs.managed(
        com.yunfeil161.ailibrary.auth.session.LoginUserContext.get().getMemberId(),
        id
      ) > 0
    ) throw new ConflictException(
      "该会话已由馆员接管，请在消息中心继续人工沟通"
    );
  }

  private void validateId(String id) {
    try {
      if (
        !UUID.fromString(id).toString().equals(id)
      ) throw new IllegalArgumentException();
    } catch (IllegalArgumentException exception) {
      throw new WrongFormatException("AI会话ID格式错误");
    }
  }
}
