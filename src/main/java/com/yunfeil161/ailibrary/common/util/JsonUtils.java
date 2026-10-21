package com.yunfeil161.ailibrary.common.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 全局 JSON 序列化与反序列化工具。
 *
 * <p>内部 ObjectMapper 在类加载时创建一次并全局复用。Jackson 的 ObjectMapper
 * 完成配置后可以安全地被多个请求线程并发调用。</p>
 */
public final class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 任意对象，允许为 null
     * @return JSON 字符串；value 为 null 时返回 {@code "null"}
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("对象序列化为JSON失败", e);
        }
    }

    /**
     * 将 JSON 反序列化为普通类型。
     */
    public static <T> T fromJson(String json, Class<T> targetType) {
        requireJson(json);
        if (targetType == null) {
            throw new IllegalArgumentException("反序列化目标类型不能为空");
        }
        try {
            return OBJECT_MAPPER.readValue(json, targetType);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }

    /**
     * 将 JSON 反序列化为带泛型的类型，例如 {@code List<Dept>}。
     */
    public static <T> T fromJson(String json, TypeReference<T> targetType) {
        requireJson(json);
        if (targetType == null) {
            throw new IllegalArgumentException("反序列化目标类型不能为空");
        }
        try {
            return OBJECT_MAPPER.readValue(json, targetType);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }

    /**
     * 使用 Jackson JavaType 反序列化复杂泛型类型。
     */
    public static <T> T fromJson(String json, JavaType targetType) {
        requireJson(json);
        if (targetType == null) {
            throw new IllegalArgumentException("反序列化目标类型不能为空");
        }
        try {
            return OBJECT_MAPPER.readValue(json, targetType);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }

    /**
     * 构造复杂泛型所需的 JavaType，例如 {@code PageResult<Dept>}。
     */
    public static JavaType parametricType(Class<?> rawType, Class<?>... parameterTypes) {
        if (rawType == null || parameterTypes == null) {
            throw new IllegalArgumentException("泛型类型不能为空");
        }
        return OBJECT_MAPPER.getTypeFactory()
                .constructParametricType(rawType, parameterTypes);
    }

    private static void requireJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON字符串不能为空");
        }
    }
}
