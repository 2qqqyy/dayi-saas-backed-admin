package com.qingmeili.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI Agent 配置（阿里云百炼 DashScope）。
 *
 * <p>全部字段都可用环境变量覆盖，方便在不同机器/环境之间切换而不改代码：
 * <pre>
 * ai.enabled              -> AI_ENABLED              是否启用大模型 Agent（关闭后自动降级为知识库 + 规则引擎）
 * ai.dashscope.api-key    -> DASHSCOPE_API_KEY       百炼 API Key
 * ai.dashscope.base-url   -> DASHSCOPE_BASE_URL      兼容模式端点
 * ai.dashscope.model      -> DASHSCOPE_MODEL         模型名，如 qwen-plus / qwen-turbo / qwen3-max
 * ai.embedding.enabled    -> AI_EMBEDDING_ENABLED    是否启用向量检索（关闭后知识库退回 2-gram 关键词检索）
 * ai.embedding.model      -> AI_EMBEDDING_MODEL      嵌入模型名，默认 text-embedding-v3
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 总开关：关闭后完全不发起大模型请求，走本地知识库与规则引擎 */
    private boolean enabled = true;

    private Dashscope dashscope = new Dashscope();

    private Embedding embedding = new Embedding();

    private Weather weather = new Weather();

    @Data
    public static class Dashscope {

        /** 百炼 API Key，缺失时不注册 Agent（服务自动降级） */
        private String apiKey;

        /** DashScope 的 OpenAI 兼容端点 */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        /** 模型名。qwen-plus 性价比高；qwen-turbo 更快；qwen3-max 效果最好 */
        private String model = "qwen-plus";

        /** 采样温度，向导类问答取较低值以保证事实稳定 */
        private Double temperature = 0.3;

        /** 单次回答最大 token 数 */
        private Integer maxTokens = 1024;

        /** 单次请求超时时间 */
        private Duration timeout = Duration.ofSeconds(60);

        /** 失败重试次数（不含首次） */
        private Integer maxRetries = 1;

        /** 是否打印请求体（调试用，会包含用户问题） */
        private boolean logRequests = false;

        /** 是否打印响应体（调试用） */
        private boolean logResponses = false;
    }

    /**
     * 知识库向量化（RAG 语义检索）配置。
     *
     * <p>它是独立于大模型对话的开关：关掉后知识库退回原来的 2-gram 关键词检索，
     * 对话模型照常工作。这样在有网络但不想消耗嵌入额度、或纯离线演示的场合都能用。
     */
    @Data
    public static class Embedding {

        /** 是否启用向量检索 */
        private boolean enabled = true;

        /** 嵌入模型 API Key。留空则复用 {@code ai.dashscope.api-key}（同一份百炼 Key） */
        private String apiKey;

        /** 嵌入模型端点。留空则复用 {@code ai.dashscope.base-url} */
        private String baseUrl;

        /** 嵌入模型名。text-embedding-v3 支持自定义维度、性价比高 */
        private String model = "text-embedding-v3";

        /** 向量维度。text-embedding-v3 支持 1024/768/512 */
        private int dimensions = 1024;

        /** 单次请求最多提交多少条文本。百炼服务端硬性上限为 10，写大了会被拒绝 */
        private int batchSize = 10;

        /** 单次请求超时时间 */
        private Duration timeout = Duration.ofSeconds(30);

        /** 失败重试次数（不含首次） */
        private int maxRetries = 1;

        /** 是否打印向量化请求体（调试用） */
        private boolean logRequests = false;

        /** 检索返回的最大条数 */
        private int topK = 3;

        /**
         * 检索命中阈值：融合分低于该值视为未命中，避免把不相关内容塞给大模型。
         *
         * <p>取值依据来自实测（8 个口语化问题 × 全量 27 篇文档）：正确答案的语义分落在 0.61-0.80，
         * 不相关文档的上限约 0.59。0.58 卡在这个缝隙上，既不漏召回、又能挡掉噪声。
         * 注意 text-embedding-v3 的余弦值区间整体偏窄，换模型后必须重新校准。
         */
        private double minScore = 0.58;

        /** 无大模型时，"命中知识库"的判定阈值（规则引擎兜底链路用，取更保守的值） */
        private double ruleMatchScore = 0.60;

        /** 正文分片的最大字数 */
        private int chunkSize = 300;

        /** 相邻正文分片的重叠字数，防止答案跨切口时两边都召回不到 */
        private int chunkOverlap = 60;

        /** 启动后是否自动做一次增量同步（只重算版本号变化/缺失的文档） */
        private boolean syncOnStartup = true;

        // ==================== 上下文接地（把命中的正文喂给大模型） ====================

        /**
         * 是否把语义命中的知识库<b>正文</b>注入 Agent 上下文。
         *
         * <p>背景：{@code AiCardFactory.platformDigest()} 原先只注入知识库<b>标题</b>列表，
         * 模型看得到"有《下雨天可以取消采摘吗？》这一条"却看不到内容，
         * 于是会自行补全细节——实测问「我临时去不了了怎么办」时编出了
         * "提前 24 小时免费取消、不足 24 小时扣 10% 手续费"，而库里的规则是
         * "未核销前全额退回、到场临时取消按 80% 退款"。属于典型的接地不足。
         */
        private boolean groundingEnabled = true;

        /**
         * 注入正文的条数上限。
         *
         * <p>只取语义命中最靠前的几篇——这是"选择性"的关键：库里条目再多，
         * 也只把与本次提问相关的原文交出去，不给上下文灌无关内容。
         */
        private int groundingTopK = 2;

        /**
         * 注入正文的命中阈值，与 {@link #minScore} 取同一口径。
         *
         * <p>注意过滤的是<b>融合分</b>（语义 + 0.12×字面），不是纯余弦分，实测分布如下：
         * <pre>
         * 「我临时去不了了怎么办」  0.627(下雨天取消…) 0.603(民宿…) 0.602 0.596 0.595 …
         * 「孩子能带进园吗」        0.679(带老人孩子…)  0.650(适合孩子的项目…) 0.599 …
         * </pre>
         * 即正确答案在 0.61+，噪声带在 0.54-0.60。0.58 卡在噪声带偏上位置：
         * 能挡住 0.54-0.58 的强噪声，又不会误伤 0.58 以上的候选。
         * 若为了"多给点参考"调到 0.50，等于把整条噪声带灌进上下文。
         *
         * <p>已知特性：「怎么」这类高频疑问词会给大量文档带来 0.3 的字面分，
         * 把语义只有 0.55 的文档抬到 0.60 —— 这是关键词加成机制的固有代价，
         * 靠调阈值解决不了，需要动 {@code KbVectorIndex.KEYWORD_BONUS} 或给疑问词做停用词处理。
         */
        private double groundingMinScore = 0.58;

        /** 单篇正文注入的最大字数，防止将来出现超长文档撑爆上下文 */
        private int groundingMaxChars = 500;
    }

    /**
     * 天气查询配置（和风天气）。
     *
     * <p>环境变量映射：
     * <pre>
     * ai.weather.enabled   -> AI_WEATHER_ENABLED      总开关
     * ai.weather.host      -> QWEATHER_API_HOST       专属 API Host（形如 abc1234xyz.re.qweatherapi.com）
     * ai.weather.key       -> QWEATHER_API_KEY        凭据里的 API Key
     * ai.weather.location  -> AI_WEATHER_LOCATION     查询坐标「经度,纬度」
     * ai.weather.label     -> AI_WEATHER_LABEL        给大模型看的地点名
     * </pre>
     *
     * <p><b>host 留空即整体不启用</b>：既不会注册 {@code WeatherClient} Bean，
     * 工具也会告知"天气服务未配置"，不会报错、不会中断对话。
     *
     * <p><b>关于 host 的坑：</b>它不是凭据 ID。和风自 2025-04 起用 API Host 取代公共域名，
     * 旧的 {@code devapi.qweather.com} 已于 2026-01-01 停服（继续用会拿到 {@code 403 Invalid Host}）。
     * 正确取值在控制台「设置」页，形如 {@code abc1234xyz.re.qweatherapi.com}。
     */
    @Data
    public static class Weather {

        /** 是否启用天气查询 */
        private boolean enabled = true;

        /** 和风天气专属 API Host。留空则不启用（对话不受影响） */
        private String host;

        /** 凭据里的 API Key */
        private String key;

        /**
         * 查询坐标，格式「经度,纬度」。
         *
         * <p>用坐标而不是城市名，是因为点位是固定的（青梅里在大邑）；
         * 若要支持游客问其他城市，需再接和风的 GeoAPI 做地名解析。
         */
        private String location = "103.51,30.58";

        /** 地点展示名，会写进给大模型的文本 */
        private String label = "青梅里（成都大邑）";

        /** 是否一并取未来 3 天预报（采摘/出行场景常问"周末天气怎么样"） */
        private boolean forecast = true;

        /**
         * 结果缓存时长。
         *
         * <p>天气更新很慢（实况 10~40 分钟、预报每天数次），30 分钟缓存足以保证信息不过期，
         * 又能把同一地点的重复提问挡在缓存里，避免白烧免费额度。
         */
        private Duration cacheTtl = Duration.ofMinutes(30);

        /** 单次请求超时。天气是"锦上添花"的信息，宁可快速失败也不要拖慢对话 */
        private Duration timeout = Duration.ofSeconds(8);
    }
}
