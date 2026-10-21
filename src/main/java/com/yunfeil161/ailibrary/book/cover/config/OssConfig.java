package com.yunfeil161.ailibrary.book.cover.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

/** 复用 Tlias 的 SDK 初始化方式；未使用封面时不要求连接 OSS。 */
@Configuration
public class OssConfig {

  @Bean(destroyMethod = "close")
  @Lazy
  public OSSClient ossClient(OssProperties properties) {
    OSSClientBuilder builder = OSSClient.newBuilder()
      .region(properties.getRegion())
      // 上传大图允许合理等待；依赖异常仍有明确超时，不无限占用图书写锁。
      .connectTimeout(java.time.Duration.ofSeconds(10))
      .readWriteTimeout(java.time.Duration.ofSeconds(60))
      .retryMaxAttempts(2)
      .credentialsProvider(new EnvironmentVariableCredentialsProvider());
    if (StringUtils.hasText(properties.getEndpoint())) {
      builder.endpoint(properties.getEndpoint());
    }
    return builder.build();
  }
}
