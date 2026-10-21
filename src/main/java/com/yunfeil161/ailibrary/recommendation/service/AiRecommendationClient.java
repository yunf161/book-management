package com.yunfeil161.ailibrary.recommendation.service;

import com.yunfeil161.ailibrary.common.util.BoundedBodyHandler;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.recommendation.dto.AiRerankResult;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 有总超时的内部模型调用。失败只影响本轮重排，不影响业务事务。 */
@Component
@Slf4j
public class AiRecommendationClient {
  @org.springframework.beans.factory.annotation.Autowired
  private com.yunfeil161.ailibrary.ai.runtime.AiRuntimeControl controls;

  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(1))
    .build();

  @Value("${library.recommendation.ai-enabled:false}")
  private boolean enabled;

  @Value(
    "${library.recommendation.ai-url:http://127.0.0.1:8001/internal/recommendations/rerank}"
  )
  private String url;

  @Value("${library.recommendation.ai-token:}")
  private String token;

  @Value("${library.recommendation.ai-timeout-seconds:30}")
  private long timeout;

  public AiRerankResult rerank(Map<String, Object> input) {
    if (!enabled || token.isBlank()) return null;
    // 暂停AI重排只回退规则算法，不阻断读者推荐功能。
    if (controls != null && !controls.enabled("RECOMMENDATION")) return null;
    try {
      var request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(timeout))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(input)))
        .build();
      // 保留整次请求超时，同时在接收过程中拒绝超长模型输出。
      var response = http.send(request, new BoundedBodyHandler(65536));
      String responseText = new String(response.body(), StandardCharsets.UTF_8);
      if (response.statusCode() != 200 || responseText.length() > 16384) {
        throw new IllegalArgumentException("模型响应状态或长度错误");
      }
      // 严格校验协议，避免JSON类型强制转换把字符串ID等错误结果悄悄接受。
      Map<?, ?> root = JsonUtils.fromJson(responseText, Map.class);
      if (
        root == null ||
        !root.keySet().equals(java.util.Set.of("items")) ||
        !(root.get("items") instanceof List<?> rows) ||
        rows.size() > 6
      ) {
        throw new IllegalArgumentException("模型结果结构错误");
      }
      List<AiRerankResult.Choice> choices = new ArrayList<>();
      for (Object row : rows) {
        if (
          !(row instanceof Map<?, ?> item) ||
          !item.keySet().equals(java.util.Set.of("bookId", "reason")) ||
          !(
            item.get("bookId") instanceof Integer ||
            item.get("bookId") instanceof Long
          ) ||
          !(item.get("reason") instanceof String reason)
        ) {
          throw new IllegalArgumentException("模型结果字段错误");
        }
        choices.add(
          new AiRerankResult.Choice(
            ((Number) item.get("bookId")).longValue(),
            reason
          )
        );
      }
      return new AiRerankResult(choices);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("AI推荐调用被中断，回退规则推荐");
    } catch (Exception exception) {
      // 不记录完整请求、模型输出或凭证；候选合法性另由Java二次校验。
      log.warn(
        "AI推荐不可用，回退规则推荐：{}",
        exception.getClass().getSimpleName()
      );
    }
    return null;
  }
}
