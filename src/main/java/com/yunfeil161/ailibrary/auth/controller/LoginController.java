package com.yunfeil161.ailibrary.auth.controller;

import com.yunfeil161.ailibrary.auth.dto.LoginInfo;
import com.yunfeil161.ailibrary.auth.dto.LoginParam;
import com.yunfeil161.ailibrary.auth.dto.RegisterParam;
import com.yunfeil161.ailibrary.auth.service.LoginService;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LoginController {

  private final LoginService loginService;

  @PostMapping("/login")
  public Result login(@RequestBody LoginParam loginParam) {
    String email = loginParam.getEmail();
    String password = loginParam.getPassword();
    log.info("登录请求：email={}", email);
    // 这里可以添加实际的登录逻辑，例如验证用户名和密码
    LoginInfo info = loginService.login(email, password);

    return Result.success(info);
  }

  @PostMapping("/logout")
  public Result logout() {
    loginService.logout();
    return Result.success("退出成功");
  }

  @PostMapping("/login/code")
  public Result loginCode(@RequestBody LoginParam loginParam) {
    String email = loginParam.getEmail();
    log.info("注册请求验证码：email={}", email);
    loginService.loginCode(email);

    return Result.success("已发送验证码");
  }

  @PostMapping("/login/register")
  public Result confirmRegister(@RequestBody RegisterParam registerParam) {
    String email = registerParam.getEmail();
    log.info("确认注册验证码：email={}", email);

    MemberType memberType = registerParam.getMemberType();
    if (memberType == null) {
      throw new WrongFormatException("注册身份不能为空");
    }

    Long memberId = switch (memberType) {
      case READER -> loginService.registerReader(registerParam);
      case EMPLOYEE -> loginService.registerEmployee(registerParam);
      case AI_LIBRARIAN -> throw new WrongFormatException("AI 馆员不支持自主注册");
    };
    return Result.success(memberId);
  }

  /** 使用邮箱验证码重置密码，不要求用户预先登录。 */
  @PostMapping("/login/reset-password")
  public Result resetPassword(@RequestBody RegisterParam registerParam) {
    loginService.resetPassword(registerParam);
    return Result.success("密码重置成功");
  }
}

