package com.yunfeil161.ailibrary.ai.knowledge;

import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Java统一鉴权，Python复用现有MD5去重、向量存储和原子生效指针。 */
@Service
@RequiredArgsConstructor
public class KnowledgeService {

  private final PermissionAccess access;
  private final KnowledgeGateway gateway;

  public Object query(String path, Map<String, String> filters) {
    access.require("knowledge:query", DataScope.ALL);
    StringJoiner query = new StringJoiner("&");
    if (filters != null) {
      for (var entry : filters.entrySet()) {
        if (
          !Set.of("page", "pageSize", "id", "title", "kind", "status").contains(
            entry.getKey()
          )
        ) throw new WrongFormatException("不支持的知识查询字段");
        query.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
      }
    }
    return gateway.request(
      "GET",
      path + (query.length() == 0 ? "" : "?" + query),
      null
    );
  }

  public Object save(String id, Map<String, Object> body) {
    access.require("knowledge:manage", DataScope.ALL);
    if (
      body == null || body.toString().length() > 900000
    ) throw new WrongFormatException("文档内容超过限制");
    return gateway.request(
      id == null ? "POST" : "PUT",
      "documents" + (id == null ? "" : "/" + identifier(id)),
      body
    );
  }

  /** 仅生成预览，不保存文件或切换索引；真正入库仍需用户确认后调用save。 */
  public Object preview(MultipartFile file) {
    access.require("knowledge:manage", DataScope.ALL);
    if (file == null || file.isEmpty()) throw new WrongFormatException(
      "请选择导入文件"
    );
    String name = file.getOriginalFilename();
    if (
      name == null ||
      name.length() > 200 ||
      name.matches("(?s).*[\\\\/\\x00\\r\\n].*")
    ) {
      throw new WrongFormatException("文件名无效，不能包含路径");
    }
    String extension = name
      .substring(name.lastIndexOf('.') + 1)
      .toLowerCase(Locale.ROOT);
    if (!Set.of("md", "txt", "csv", "json", "pdf").contains(extension)) {
      throw new WrongFormatException("仅支持MD、TXT、CSV、JSON和文本型PDF");
    }
    long limit = extension.equals("pdf") ? 4L * 1024 * 1024 : 600000;
    if (file.getSize() > limit) throw new WrongFormatException(
      "PDF最大4MiB，其他格式最大600000字节"
    );
    try {
      return gateway.request(
        "POST",
        "import-preview",
        Map.of(
          "fileName",
          name,
          "contentBase64",
          Base64.getEncoder().encodeToString(file.getBytes())
        )
      );
    } catch (IOException error) {
      throw new WrongFormatException("读取导入文件失败，请重新选择");
    }
  }

  public Object rebuild(Map<String, Object> body) {
    access.require("knowledge:rebuild", DataScope.ALL);
    return gateway.request("POST", "rebuild", body);
  }

  public Object search(Map<String, Object> body) {
    access.require("knowledge:query", DataScope.ALL);
    return gateway.request("POST", "search", body);
  }

  public String identifier(String id) {
    if (
      id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")
    ) throw new WrongFormatException("文档或任务ID格式错误");
    return id;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
