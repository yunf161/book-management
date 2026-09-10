package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Reader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读者仓储的内存实现。
 */
public class InMemoryReaderRepository implements ReaderRepository {
    /** 主存储，按 readerId 保存读者。 */
    private final MainMemoryTemp<Reader> baseRepository;
    /** 二级索引：phone -> readerId，用于手机号快速查询。 */
    private final Map<String, String> phoneToReaderId;
    /** 反向索引：readerId -> phone，用于更新手机号时清理旧索引。 */
    private final Map<String, String> readerIdToPhone;

    /** 初始化读者内存仓储。 */
    public InMemoryReaderRepository() {
        this.baseRepository = new MainMemoryTemp<>(Reader::getReaderId);
        this.phoneToReaderId = new ConcurrentHashMap<>();
        this.readerIdToPhone = new ConcurrentHashMap<>();
    }

    /** 保存读者并更新手机号索引。 */
    @Override
    public void save(Reader reader) {
        if (reader.getReaderId() == null || reader.getReaderId().isBlank()) {
            throw new IllegalArgumentException("readerId cannot be null or blank");
        }
        if (reader.getPhone_number() == null || reader.getPhone_number().isBlank()) {
            throw new IllegalArgumentException("phoneNumber cannot be null or blank");
        }
        String oldPhone = readerIdToPhone.get(reader.getReaderId());
        if (oldPhone != null && !oldPhone.equals(reader.getPhone_number())) {
            phoneToReaderId.remove(oldPhone);
        }
        baseRepository.save(reader);
        phoneToReaderId.put(reader.getPhone_number(), reader.getReaderId());
        readerIdToPhone.put(reader.getReaderId(), reader.getPhone_number());
    }

    /** 按读者编号查询。 */
    @Override
    public Optional<Reader> findById(String readerId) {
        return baseRepository.findById(readerId);
    }

    /** 按手机号查询。 */
    @Override
    public Optional<Reader> findByPhoneNumber(String phoneNumber) {
        String readerId = phoneToReaderId.get(phoneNumber);
        if (readerId == null) {
            return Optional.empty();
        }
        return baseRepository.findById(readerId);
    }

    /** 删除读者并同步清理手机号索引。 */
    @Override
    public void deleteById(String readerId) {
        Optional<Reader> existingReader = baseRepository.findById(readerId);
        if (existingReader.isPresent()) {
            String oldPhone = readerIdToPhone.remove(readerId);
            if (oldPhone != null) {
                phoneToReaderId.remove(oldPhone);
            }
        }
        baseRepository.deleteById(readerId);
    }
}
