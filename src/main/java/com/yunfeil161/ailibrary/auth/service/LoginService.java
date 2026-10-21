package com.yunfeil161.ailibrary.auth.service;

import com.yunfeil161.ailibrary.auth.dto.LoginInfo;
import com.yunfeil161.ailibrary.auth.dto.RegisterParam;
import org.springframework.stereotype.Service;

@Service
public interface LoginService {
  LoginInfo login(String email, String password);

  void logout();

  void loginCode(String email);

  Long registerReader(RegisterParam registerParam);

  Long registerEmployee(RegisterParam registerParam);

  /** 校验邮箱验证码后重置成员密码。 */
  void resetPassword(RegisterParam registerParam);
}
