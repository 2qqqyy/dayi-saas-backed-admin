package com.qingmeili.common.util;

import java.util.Random;

/**
 * 随机数工具类
 */
public class RandomUtil {

    private static final Random RANDOM = new Random();

    /**
     * 生成指定长度的数字字符串
     */
    public static String randomNumbers(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的随机字符串（数字+字母）
     */
    public static String randomString(int length) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
