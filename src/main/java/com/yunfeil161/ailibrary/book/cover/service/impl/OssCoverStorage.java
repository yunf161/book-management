package com.yunfeil161.ailibrary.book.cover.service.impl;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.yunfeil161.ailibrary.book.cover.config.OssProperties;
import com.yunfeil161.ailibrary.book.cover.service.CoverStorage;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import java.time.Instant;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 从 Tlias 上传实现扩展出删除与私有对象签名读取能力。 */
@Service
public class OssCoverStorage implements CoverStorage {

  private final OSSClient client;
  private final OssProperties properties;

  public OssCoverStorage(@Lazy OSSClient client, OssProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public void upload(String key, byte[] content, String contentType) {
    try {
      client.putObject(
        PutObjectRequest.newBuilder()
          .bucket(properties.getBucket())
          .key(key)
          .body(BinaryData.fromBytes(content))
          .contentType(contentType)
          .build()
      );
      // 请求由服务端上传，随后核对 OSS 元数据，成功后才允许关联图书。
      HeadObjectResult result = client.headObject(
        HeadObjectRequest.newBuilder()
          .bucket(properties.getBucket())
          .key(key)
          .build()
      );
      if (result.contentLength() != content.length || !contentType.equalsIgnoreCase(result.contentType())) {
        throw new IllegalStateException("OSS 对象大小或类型核验失败");
      }
    } catch (RuntimeException failure) {
      throw new ServiceUnavailableException(
        "图书封面上传失败，请稍后重试",
        failure
      );
    }
  }

  @Override
  public String signedUrl(String key) {
    try {
      return client
        .presign(
          GetObjectRequest.newBuilder()
            .bucket(properties.getBucket())
            .key(key)
            .build(),
          PresignOptions.newBuilder()
            .expiration(
              Instant.now().plusSeconds(properties.getSignedUrlSeconds())
            )
            .build()
        )
        .url();
    } catch (RuntimeException failure) {
      throw new ServiceUnavailableException(
        "图书封面读取失败，请稍后重试",
        failure
      );
    }
  }

  @Override
  public void delete(String key) {
    try {
      client.deleteObject(
        DeleteObjectRequest.newBuilder()
          .bucket(properties.getBucket())
          .key(key)
          .build()
      );
    } catch (RuntimeException failure) {
      throw new ServiceUnavailableException("OSS 封面对象删除失败", failure);
    }
  }
}
