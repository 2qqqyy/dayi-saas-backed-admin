package com.qingmeili.common.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 敏感字段脱敏序列化器：接口返回永远不给完整证件号/银行卡/手机号。
 * 身份证保留前 4 后 4，银行卡保留后 4，手机号保留前 3 后 4。
 */
public final class MaskSerializers {

    private MaskSerializers() {
    }

    public static String idCard(String v) {
        if (v == null || v.length() < 9) {
            return v;
        }
        return v.substring(0, 4) + "********" + v.substring(v.length() - 4);
    }

    public static String bankCard(String v) {
        if (v == null || v.length() < 5) {
            return v;
        }
        String digits = v.replaceAll("\\D", "");
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    public static String mobile(String v) {
        if (v == null || v.length() < 8) {
            return v;
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }

    public static class IdCard extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(idCard(value));
        }
    }

    public static class BankCard extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(bankCard(value));
        }
    }

    public static class Mobile extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(mobile(value));
        }
    }
}
