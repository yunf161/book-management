package com.yunfeil161.ailibrary.book.borrow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 独立启用借阅模块的定时任务。 */
@Configuration
@EnableScheduling
public class BorrowSchedulingConfig {
}
