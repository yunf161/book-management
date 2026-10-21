package com.yunfeil161.ailibrary.ai.business;

import com.yunfeil161.ailibrary.common.util.BoundedBodyHandler;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 仅回调本Java应用的白名单路由，原Controller/Service继续负责全部业务鉴权和事务。 */
@Component
public class BusinessHttpClient {

  @org.springframework.beans.factory.annotation.Autowired
  private com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation correlation;

  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(1))
    .build();

  @Value("${library.ai.business-base-url:http://127.0.0.1:${server.port:8080}}")
  private String baseUrl;

  public record Outcome(
    boolean known,
    int code,
    Map<?, ?> response,
    String refreshedToken
  ) {}

  public Outcome execute(
    BusinessSkillRegistry.Skill skill,
    String path,
    Map<String, Object> body,
    String token
  ) {
    try {
      var builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
      String actionId =
        com.yunfeil161.ailibrary.ai.runtime.AiActionCorrelation.current();
      if (actionId != null && correlation != null) {
        builder
          .header("X-AI-Action-Id", actionId)
          .header(
            "X-AI-Action-Signature",
            correlation.sign(
              actionId,
              skill.method(),
              path,
              com.yunfeil161.ailibrary.auth.session.LoginUserContext.get().getMemberId()
            )
          );
      }
      var request = builder
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .method(
          skill.method(),
          HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body))
        )
        .build();
      var response = http.send(request, new BoundedBodyHandler(500000));
      Map<?, ?> result = JsonUtils.fromJson(
        new String(response.body(), StandardCharsets.UTF_8),
        Map.class
      );
      if (
        !(result.get("code") instanceof Number code) ||
        code.intValue() != response.statusCode()
      ) return uncertain();
      // 5xx可能发生在数据库提交后，不能据此认定未执行，更不能自动重试。
      return new Outcome(
        response.statusCode() < 500,
        response.statusCode(),
        result,
        response.headers().firstValue("X-Refreshed-Token").orElse(null)
      );
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return uncertain();
    } catch (Exception exception) {
      return uncertain();
    }
  }

  private Outcome uncertain() {
    return new Outcome(
      false,
      503,
      Map.of(
        "code",
        503,
        "msg",
        "结果待核实，请查询实际业务状态，勿重复提交",
        "data",
        Map.of()
      ),
      null
    );
  }
}
