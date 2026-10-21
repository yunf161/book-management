package com.yunfeil161.ailibrary.config.security;

import java.security.SecureRandom;

public final class VerificationCodeUtils {

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private VerificationCodeUtils() {
    }

    /**
     * 生成六位数字验证码，允许以0开头。
     */
    public static String generateSixDigitCode() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}