package com.yunfeil161.ailibrary.book.cover.service;

/** 隔离 OSS 调用，测试可替换存储实现而不改变业务流程。 */
public interface CoverStorage {
  void upload(String key, byte[] content, String contentType);
  String signedUrl(String key);
  void delete(String key);
}
