package com.yunfeil161.ailibrary.book.cover.dto;

/** coverUrl 是稳定的应用读取地址，每次访问再生成短期 OSS 签名。 */
public record BookCoverResponse(
  Long bookId,
  String coverObjectKey,
  String coverUrl,
  Long version
) {}
