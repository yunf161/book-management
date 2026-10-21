package com.yunfeil161.ailibrary.ai.runtime;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 服务间签名将业务审计与AI操作关联，浏览器不能伪造执行凭据。 */
@Component
public class AiActionCorrelation {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private final JdbcTemplate jdbc;

  @Value("${library.recommendation.ai-token:}")
  private String secret;

  public AiActionCorrelation(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static void set(String id) {
    CURRENT.set(id);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public static String current() {
    return CURRENT.get();
  }

  public String sign(String id, String method, String path, Long member) {
    if (secret == null || secret.length() < 24) return "";
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
      );
      return HexFormat.of().formatHex(
        mac.doFinal(
          (id + "\n" + method + "\n" + path + "\n" + member).getBytes(
            StandardCharsets.UTF_8
          )
        )
      );
    } catch (java.security.GeneralSecurityException error) {
      throw new IllegalStateException("内部审计签名失败", error);
    }
  }

  public String verified(HttpServletRequest request) {
    var session = LoginUserContext.getNullable();
    String id = request.getHeader("X-AI-Action-Id"),
      signature = request.getHeader("X-AI-Action-Signature");
    if (
      session == null ||
      id == null ||
      !id.matches("[a-f0-9-]{36}") ||
      signature == null ||
      signature.length() != 64
    ) return null;
    String expected = sign(
      id,
      request.getMethod(),
      request.getRequestURI(),
      session.getMemberId()
    );
    if (
      expected.isEmpty() ||
      !MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8)
      )
    ) return null;
    return jdbc.queryForObject(
      "select count(*) from ai_business_action where action_id=? and member_id=? and state in ('EXECUTING','UNKNOWN')",
      Integer.class,
      id,
      session.getMemberId()
    ) == 1
      ? id
      : null;
  }
}
