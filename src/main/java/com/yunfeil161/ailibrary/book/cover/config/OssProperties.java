package com.yunfeil161.ailibrary.book.cover.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** OSS资源由部署环境指定；凭据由SDK从环境变量读取，不绑定开发者的Bucket。 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

  private String region = "cn-shenzhen";
  private String bucket = "";
  private String endpoint = "https://oss-cn-shenzhen.aliyuncs.com";
  private long maxCoverBytes = 5 * 1024 * 1024;
  private long signedUrlSeconds = 300;
}
