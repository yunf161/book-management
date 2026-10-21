package com.yunfeil161.ailibrary.common.util;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilsTests {

    @Test
    void serializesAndDeserializesObjectWithJavaTime() {
        Sample source = new Sample(1L, "测试", LocalDateTime.of(2026, 9, 26, 20, 0));

        String json = JsonUtils.toJson(source);
        Sample restored = JsonUtils.fromJson(json, Sample.class);

        assertEquals(source, restored);
    }

    @Test
    void deserializesGenericCollection() {
        List<Sample> source = List.of(
                new Sample(1L, "第一项", LocalDateTime.of(2026, 9, 26, 20, 0)),
                new Sample(2L, "第二项", LocalDateTime.of(2026, 9, 26, 21, 0))
        );

        String json = JsonUtils.toJson(source);
        List<Sample> restored = JsonUtils.fromJson(
                json,
                new TypeReference<List<Sample>>() { }
        );

        assertEquals(source, restored);
    }

    @Test
    void rejectsBlankJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonUtils.fromJson(" ", Sample.class)
        );
    }

    private record Sample(Long id, String name, LocalDateTime createdAt) {
    }
}
