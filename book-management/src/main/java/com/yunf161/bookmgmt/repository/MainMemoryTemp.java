package com.yunf161.bookmgmt.repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 通用内存仓储：
 * 通过主键提取函数将任意实体存入 ConcurrentMap，提供基础 CRUD。
 */
public class MainMemoryTemp<T> {
    /** 主存储结构，key 为实体主键。 */
    private final ConcurrentMap<String, T> dataMap;
    /** 从实体中提取主键的函数。 */
    private final Function<T, String> idExtractor;

    /** 构造通用内存仓储。 */
    public MainMemoryTemp(Function<T, String> idExtractor) {
        this.dataMap = new ConcurrentHashMap<>();
        this.idExtractor = idExtractor;
    }

    /** 保存实体（存在则覆盖，不存在则新增）。 */
    public void save(T value) {
        String id = idExtractor.apply(value);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Entity id cannot be null or blank");
        }
        dataMap.put(id, value);
    }

    /** 按主键查询实体。 */
    public Optional<T> findById(String key) {
        return Optional.ofNullable(dataMap.get(key));
    }

    /** 判断主键是否存在。 */
    public boolean existsById(String key) {
        return dataMap.containsKey(key);
    }

    /** 查询全部实体。 */
    public List<T> findAll() {
        return new ArrayList<>(dataMap.values());
    }

    /** 按主键删除实体。 */
    public void deleteById(String key) {
        if (dataMap.remove(key) == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }
    }
}
