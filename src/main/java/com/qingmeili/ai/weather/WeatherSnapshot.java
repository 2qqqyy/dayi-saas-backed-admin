package com.qingmeili.ai.weather;

import java.util.List;

/**
 * 一次天气快照：实况 + 未来几天预报。
 *
 * <p>刻意做成不可变的 record，并且自带 {@link #toText()} —— 大模型看到的永远是一段
 * 固定格式的中文，而不是原始 JSON。理由和知识库接地一致：模型读到的字段越干净，
 * 越不容易把"湿度 60"说成"降水量 60mm"这类串味错误。
 *
 * @param label      地点展示名（如「青梅里（成都大邑）」）
 * @param updateTime 数据发布时间，用于让模型知道信息的新鲜度
 * @param text       实况天气现象（晴 / 多云 / 小雨 …）
 * @param temp       实况气温（℃）
 * @param feelsLike  体感温度（℃）
 * @param humidity   相对湿度（%）
 * @param windDir    风向描述
 * @param windScale  风力等级
 * @param daily      未来几天预报，可能为空（未开启或接口未返回）
 */
public record WeatherSnapshot(
        String label,
        String updateTime,
        String text,
        String temp,
        String feelsLike,
        String humidity,
        String windDir,
        String windScale,
        List<Daily> daily) {

    /** 单日预报 */
    public record Daily(
            String date,
            String textDay,
            String textNight,
            String tempMax,
            String tempMin,
            String precip) {
    }

    /** 渲染成给大模型阅读的紧凑中文文本 */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(has(label) ? label : "当前地点").append("：");
        sb.append(has(text) ? text : "—");
        if (has(temp)) {
            sb.append("，气温 ").append(temp).append("℃");
        }
        if (has(feelsLike)) {
            sb.append("（体感 ").append(feelsLike).append("℃）");
        }
        if (has(humidity)) {
            sb.append("，湿度 ").append(humidity).append("%");
        }
        if (has(windDir) || has(windScale)) {
            sb.append("，");
            if (has(windDir)) {
                sb.append(windDir);
            }
            if (has(windScale)) {
                sb.append(" ").append(windScale).append("级");
            }
        }
        if (has(updateTime)) {
            sb.append("。数据时间：").append(updateTime);
        }

        if (daily != null && !daily.isEmpty()) {
            sb.append("\n未来天气预报：");
            for (Daily d : daily) {
                sb.append("\n  ").append(has(d.date()) ? d.date() : "—").append(" ");
                sb.append(has(d.textDay()) ? d.textDay() : "—");
                // 只有在昼夜天气不同时才写"转"，否则"晴转晴"很啰嗦
                if (has(d.textNight()) && has(d.textDay()) && !d.textNight().equals(d.textDay())) {
                    sb.append("转").append(d.textNight());
                }
                if (has(d.tempMin()) || has(d.tempMax())) {
                    sb.append(" ").append(nz(d.tempMin())).append("~").append(nz(d.tempMax())).append("℃");
                }
                if (has(d.precip()) && !"0.0".equals(d.precip())) {
                    sb.append("，降水 ").append(d.precip()).append("mm");
                }
            }
        }
        return sb.toString();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
