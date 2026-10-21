package com.yunfeil161.ailibrary.ai.knowledge;

import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.BoundedBodyHandler;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 只连接配置中的内部AI服务；不转发用户JWT，不接受客户端目标URL。 */
@Component
public class KnowledgeGateway {

  @Value("${library.ai.url:http://127.0.0.1:8001}")
  private String url;

  @Value("${library.recommendation.ai-token:}")
  private String secret;

  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

  public Object request(String method, String path, Object input) {
    if (
      secret == null || secret.isBlank()
    ) throw new ServiceUnavailableException("知识库内部服务尚未配置");
    try {
      var request = HttpRequest.newBuilder(
        URI.create(url + "/internal/knowledge/" + path)
      )
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + secret)
        .header("Content-Type", "application/json")
        .method(
          method,
          input == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(input))
        )
        .build();
      var response = client.send(request, new BoundedBodyHandler(2000000));
      String body = new String(response.body(), StandardCharsets.UTF_8);
      if (response.statusCode() == 200) return JsonUtils.fromJson(
        body,
        Object.class
      );
      String message = "知识库请求未完成";
      if (response.statusCode() < 500) {
        var error = JsonUtils.fromJson(body, Map.class);
        if (error.get("error") instanceof String text) message = text;
      }
      switch (response.statusCode()) {
        case 400 -> throw new WrongFormatException(message);
        case 404 -> throw new NotFoundException(message);
        case 409 -> throw new ConflictException(message);
        default -> throw new ServiceUnavailableException(
          "知识库或嵌入服务暂不可用"
        );
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ServiceUnavailableException("知识库请求已中断");
    } catch (java.io.IOException | IllegalArgumentException error) {
      throw new ServiceUnavailableException("知识库服务暂不可用");
    }
  }
}
