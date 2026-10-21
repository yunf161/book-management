package com.yunfeil161.ailibrary.common.redis.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class redisExpire {
    private  Object data;
    private LocalDateTime expireTime;
}
