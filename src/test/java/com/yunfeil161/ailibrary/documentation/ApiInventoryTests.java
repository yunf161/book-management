package com.yunfeil161.ailibrary.documentation;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 从实际注册的Spring路由与请求类型导出接口索引，不启动生产依赖或读取业务数据。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:api_inventory;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class ApiInventoryTests {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Test
  void controllerInventoryMatchesCurrentApplication() throws Exception {
    var endpoints = new ArrayList<Map<String, Object>>();
    var models = new TreeMap<String, Object>();
    mappings.getHandlerMethods().forEach((mapping, handler) -> {
      if (
        !handler.getBeanType().getName().startsWith("com.yunfeil161.ailibrary")
      ) return;
      var params = new ArrayList<Map<String, Object>>();
      for (var parameter : handler.getMethodParameters()) {
        var type = parameter.getParameterType();
        if (type.getName().startsWith("jakarta.servlet")) continue;
        var item = new LinkedHashMap<String, Object>();
        item.put("name", parameter.getParameterName());
        item.put("type", parameter.getGenericParameterType().getTypeName());
        item.put("rawType", type.getName());
        item.put("binding", binding(parameter));
        if (type.isEnum()) {
          item.put(
            "enum",
            Arrays.stream(type.getEnumConstants())
              .map(Object::toString)
              .filter(value -> {
                try {
                  return !type
                    .getField(value)
                    .isAnnotationPresent(Deprecated.class);
                } catch (NoSuchFieldException e) {
                  throw new IllegalStateException(e);
                }
              })
              .toList()
          );
        }
        var annotations = new LinkedHashMap<String, Object>();
        for (Annotation annotation : parameter.getParameterAnnotations()) {
          var values = new LinkedHashMap<String, Object>();
          for (var method : annotation.annotationType().getDeclaredMethods()) {
            try {
              Object value = method.invoke(annotation);
              if (
                value instanceof String || value instanceof Boolean
              ) values.put(method.getName(), value);
            } catch (ReflectiveOperationException e) {
              throw new IllegalStateException(e);
            }
          }
          annotations.put(annotation.annotationType().getSimpleName(), values);
        }
        item.put("annotations", annotations);
        params.add(item);
        if (
          type.getName().startsWith("com.yunfeil161.ailibrary") &&
          !type.isEnum()
        ) model(type, models);
      }
      for (var path : mapping.getPatternValues()) {
        for (var method : mapping.getMethodsCondition().getMethods()) {
          var endpoint = new LinkedHashMap<String, Object>();
          endpoint.put("path", path);
          endpoint.put("method", method.name());
          endpoint.put("controller", handler.getBeanType().getName());
          endpoint.put("handler", handler.getMethod().getName());
          endpoint.put(
            "returnType",
            handler.getMethod().getGenericReturnType().getTypeName()
          );
          endpoint.put(
            "consumes",
            mapping
              .getConsumesCondition()
              .getConsumableMediaTypes()
              .stream()
              .map(Object::toString)
              .toList()
          );
          endpoint.put(
            "produces",
            mapping
              .getProducesCondition()
              .getProducibleMediaTypes()
              .stream()
              .map(Object::toString)
              .toList()
          );
          endpoint.put("parameters", params);
          endpoints.add(endpoint);
        }
      }
    });
    endpoints.sort(
      Comparator.comparing(e -> e.get("path") + " " + e.get("method"))
    );
    assertTrue(endpoints.size() > 80);
    assertTrue(
      endpoints.stream().anyMatch(e -> e.get("path").equals("/ai/chat"))
    );
    assertTrue(
      endpoints.stream().anyMatch(e -> e.get("path").equals("/admin/members"))
    );
    assertFalse(
      endpoints
        .stream()
        .anyMatch(e -> e.get("handler").toString().contains("AiLibrarian"))
    );
    if (Boolean.getBoolean("docs.export")) {
      Path folder = Path.of("target/project-delivery");
      Files.createDirectories(folder);
      Files.writeString(
        folder.resolve("api-inventory.json"),
        JsonUtils.toJson(Map.of("endpoints", endpoints, "models", models))
      );
    }
  }

  private String binding(MethodParameter parameter) {
    if (parameter.hasParameterAnnotation(RequestBody.class)) return "body";
    if (parameter.hasParameterAnnotation(PathVariable.class)) return "path";
    if (parameter.hasParameterAnnotation(RequestHeader.class)) return "header";
    if (parameter.hasParameterAnnotation(RequestPart.class)) return "part";
    if (parameter.hasParameterAnnotation(RequestParam.class)) return "query";
    return "model";
  }

  private void model(Class<?> type, Map<String, Object> models) {
    if (models.containsKey(type.getName())) return;
    var fields = new LinkedHashMap<String, Object>();
    models.put(type.getName(), fields);
    for (
      var current = type;
      current != null && current != Object.class;
      current = current.getSuperclass()
    ) {
      for (var field : current.getDeclaredFields()) {
        if (
          Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
        ) continue;
        var info = new LinkedHashMap<String, Object>();
        info.put("type", field.getGenericType().getTypeName());
        info.put("rawType", field.getType().getName());
        if (field.getType().isEnum()) {
          // 兼容旧数据的弃用值不作为当前接口可选项。
          info.put(
            "enum",
            Arrays.stream(field.getType().getEnumConstants())
              .map(Object::toString)
              .filter(value -> {
                try {
                  return !field
                    .getType()
                    .getField(value)
                    .isAnnotationPresent(Deprecated.class);
                } catch (NoSuchFieldException e) {
                  throw new IllegalStateException(e);
                }
              })
              .toList()
          );
        }
        fields.putIfAbsent(field.getName(), info);
        if (
          field.getType().getName().startsWith("com.yunfeil161.ailibrary") &&
          !field.getType().isEnum()
        ) model(field.getType(), models);
      }
    }
  }
}
