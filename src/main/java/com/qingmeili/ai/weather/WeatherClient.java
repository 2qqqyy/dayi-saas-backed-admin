package com.qingmeili.ai.weather;

/**
 * 天气数据源接口。
 *
 * <p>抽成接口是为了让"换一家天气服务商"只是一次 Bean 替换 ——
 * 上层（Agent 工具、上下文注入）只认 {@link #snapshot()} 的返回值，不关心是和风还是别的。
 *
 * <p><b>降级契约（与向量检索同一套原则）：</b>本接口所有方法都不抛异常给业务层。
 * 服务不可用返回 {@code null}，由调用方决定怎么措辞——
 * 天气查不到绝不能让游客的对话中断。
 */
public interface WeatherClient {

    /** 服务是否可用（配置齐全）。用于工具侧决定是否注册/是否提示降级 */
    boolean available();

    /**
     * 取当前天气快照。
     *
     * @return 快照；调用失败且无历史缓存时返回 {@code null}
     */
    WeatherSnapshot snapshot();

    /** 数据源名称，用于状态展示与日志 */
    String provider();
}
