package com.yunfeil161.ailibrary.ai.business;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.api.*;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import com.yunfeil161.ailibrary.config.security.jwt.JwtUtils;
import jakarta.servlet.http.*;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 显式确认与模型生成分离，模型服务没有确认端点的访问能力。 */
@RestController
@RequiredArgsConstructor
public class BusinessActionController {

  private final BusinessActionService service;
  private final JwtUtils jwt;

  @GetMapping("/ai/skills")
  public Result skills() {
    return Result.success(service.skills());
  }

  @PostMapping("/ai/actions/preview")
  public Result preview(@RequestBody BusinessActionService.Preview request) {
    return Result.success(service.preview(request));
  }

  @GetMapping("/ai/actions")
  public Result query(
    @RequestParam(required = false) String actionId,
    @RequestParam(required = false) String state,
    @ModelAttribute PageQuery page
  ) {
    return Result.success(service.query(actionId, state, page));
  }

  @PostMapping("/ai/actions/{actionId}/confirm")
  public Result confirm(
    @PathVariable String actionId,
    @RequestBody BusinessActionService.Confirmation confirmation,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    String token = response.getHeader("X-Refreshed-Token");
    String header = request.getHeader("Authorization");
    if (token == null && header != null && header.startsWith("Bearer ")) token =
      header.substring(7).trim();
    var execution = service.confirm(actionId, confirmation, token);
    if (execution.refreshedToken() != null) {
      try {
        if (
          !Objects.equals(
            String.valueOf(
              jwt.parseToken(execution.refreshedToken()).get("memberId")
            ),
            LoginUserContext.get().getMemberId().toString()
          )
        ) throw new IllegalArgumentException();
        response.setHeader("X-Refreshed-Token", execution.refreshedToken());
      } catch (RuntimeException exception) {
        throw new ServiceUnavailableException(
          "操作完成但会话轮换结果异常，请重新登录后查询业务状态"
        );
      }
    }
    return Result.success(execution.action());
  }

  @PostMapping("/ai/actions/{actionId}/cancel")
  public Result cancel(@PathVariable String actionId) {
    return Result.success(service.cancel(actionId));
  }
}
