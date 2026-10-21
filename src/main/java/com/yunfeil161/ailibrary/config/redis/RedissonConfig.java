package com.yunfeil161.ailibrary.config.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** 使用与 Spring Data Redis 相同的单机 Redis 参数创建 Redisson 客户端。 */
@Configuration
public class RedissonConfig {

  @Bean(destroyMethod = "shutdown")
  public RedissonClient redissonClient(
    @Value("${spring.data.redis.host:127.0.0.1}") String host,
    @Value("${spring.data.redis.port:6379}") int port,
    @Value("${spring.data.redis.password:}") String password,
    @Value("${spring.data.redis.database:0}") int database,
    @Value("${spring.data.redis.timeout:3s}") java.time.Duration timeout
  ) {
    Config config = new Config();
    SingleServerConfig server = config
      .useSingleServer()
      .setAddress("redis://" + host + ":" + port)
      .setDatabase(database)
      .setTimeout((int) timeout.toMillis());

    if (StringUtils.hasText(password)) {
      server.setPassword(password);
    }

    return Redisson.create(config);
  }
}
