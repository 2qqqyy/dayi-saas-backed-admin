package com.qingmeili.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingmeili.ai.AiAgentTools;
import com.qingmeili.ai.AiProperties;
import com.qingmeili.ai.ContentCopyAgent;
import com.qingmeili.ai.QingmeiliAgent;
import com.qingmeili.ai.embedding.DashScopeEmbeddingClient;
import com.qingmeili.ai.embedding.EmbeddingClient;
import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.ai.weather.QWeatherClient;
import com.qingmeili.ai.weather.WeatherClient;
import com.qingmeili.mapper.KbDocumentMapper;
import com.qingmeili.mapper.KbEmbeddingMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AI Agent 装配：把阿里云百炼（DashScope）接成一个可被业务直接调用的 {@link QingmeiliAgent}。
 *
 * <p>为什么走 OpenAI 协议而不是 DashScope 私有 SDK：百炼提供官方的 OpenAI 兼容端点
 * （{@code /compatible-mode/v1}），走标准协议意味着模型可随时换成 qwen-turbo、qwen3-max
 * 甚至其他兼容 OpenAI 协议的服务，只需改配置，不用动代码。
 *
 * <p>降级策略：{@code ai.enabled=false} 或未配置 api-key 时，下面两个 Bean 根本不会创建，
 * 业务侧通过 {@code ObjectProvider} 取用，取不到就自动回退到本地知识库 + 规则引擎——
 * 保证演示环境断网、无额度时对话功能依然可用。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAgentConfig {

    /** 启用条件：开关打开，且 api-key 非空 */
    private static final String AGENT_ENABLED =
            "'${ai.enabled:true}' == 'true' and '${ai.dashscope.api-key:}'.trim().length() > 0";

    /**
     * 向量检索的启用条件。刻意<b>不</b>依赖 {@code ai.enabled}：
     * 关掉对话大模型（纯离线演示）与关掉向量检索是两个独立诉求，
     * 只要嵌入开关打开且拿得到百炼 Key，就应该让知识库享受语义检索。
     */
    private static final String EMBEDDING_ENABLED =
            "'${ai.embedding.enabled:true}' == 'true' and '${ai.dashscope.api-key:}'.trim().length() > 0";

    /**
     * 天气查询的启用条件：开关打开，且填了 API Host。
     *
     * <p>之所以用 host 而不是 key 作为判据：和风的 key 单独填错也能通过配置校验，
     * 而 host 缺失是"根本调不通"的确定性标志——留空即整体关闭，语义最清晰。
     */
    private static final String WEATHER_ENABLED =
            "'${ai.weather.enabled:true}' == 'true' and '${ai.weather.host:}'"
                    + ".replace('https://','').replace('http://','').trim().length() > 0";

    /**
     * 对话模型。构建时不发起网络请求，只有真正调用时才连接百炼。
     */
    @Bean
    @ConditionalOnExpression(AGENT_ENABLED)
    public ChatModel dashscopeChatModel(AiProperties properties) {
        AiProperties.Dashscope ds = properties.getDashscope();
        log.info("AI Agent 初始化完成：provider=DashScope(OpenAI 兼容端点)，model={}，baseUrl={}",
                ds.getModel(), ds.getBaseUrl());
        return OpenAiChatModel.builder()
                .apiKey(ds.getApiKey())
                .baseUrl(ds.getBaseUrl())
                .modelName(ds.getModel())
                .temperature(ds.getTemperature())
                .maxTokens(ds.getMaxTokens())
                .timeout(ds.getTimeout())
                .maxRetries(ds.getMaxRetries())
                // 百炼兼容模式已支持 json_schema 严格模式，开启后输出必然符合 AgentReply 结构
                .strictJsonSchema(true)
                .logRequests(ds.isLogRequests())
                .logResponses(ds.isLogResponses())
                .build();
    }

    /**
     * 山野向导 Agent：模型 + 工具集 + 会话记忆三者组装。
     *
     * <p>与模型 Bean 使用完全相同的启用条件，因此二者要么都在、要么都不在，
     * 不存在「模型缺失导致 Agent 创建失败」的中间态。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression(AGENT_ENABLED)
    public QingmeiliAgent qingmeiliAgent(ChatModel dashscopeChatModel, AiAgentTools tools) {
        return AiServices.builder(QingmeiliAgent.class)
                .chatModel(dashscopeChatModel)
                .tools(tools)
                // 按会话保留最近 4 轮（8 条）消息，支持「那住宿呢」这类省略主语的追问。
                // 条数取得偏保守：每条用户消息里都带着一份平台资料摘要，留太长会明显抬高单次请求的 token
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(8)
                        .build())
                // 模型自己拼错的参数直接把原因回给它，让它自行纠正重试
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                // 工具内部异常（SQL 报错等）可能带库表名、路径等内部信息，不上报给模型，
                // 而是让本次调用失败，由 AiAgentServiceImpl 捕获后回退规则引擎，用库内真实数据兜底
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())
                .build();
    }

    /**
     * 后台文案 Agent：刻意不注册任何工具。
     *
     * <p>因为百炼的结构化输出（json_schema）与函数调用同时开启时，模型会倾向于只吐 JSON、跳过工具调用。
     * 文案生成本来就不需要查库（真实素材由调用方放进简报），所以这里保持「纯结构化输出」，
     * 反过来保证 {@link com.qingmeili.ai.MarketingCopy} 的解析稳定。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression(AGENT_ENABLED)
    public ContentCopyAgent contentCopyAgent(ChatModel dashscopeChatModel) {
        return AiServices.builder(ContentCopyAgent.class)
                .chatModel(dashscopeChatModel)
                .build();
    }

    // ==================== 知识库向量化（RAG） ====================

    /**
     * 文本嵌入客户端：复用对话模型同一套百炼 Key 与兼容端点，只把 model 换成 text-embedding-v3。
     *
     * <p>与对话模型共享 Key 是为了少一份配置、少一处泄漏面；
     * 需要独立额度或换别家嵌入服务时，单独填 {@code ai.embedding.api-key / base-url} 即可覆盖。
     */
    @Bean
    @ConditionalOnExpression(EMBEDDING_ENABLED)
    public EmbeddingClient embeddingClient(AiProperties properties) {
        AiProperties.Dashscope ds = properties.getDashscope();
        AiProperties.Embedding emb = properties.getEmbedding();
        String apiKey = StringUtils.hasText(emb.getApiKey()) ? emb.getApiKey() : ds.getApiKey();
        String baseUrl = StringUtils.hasText(emb.getBaseUrl()) ? emb.getBaseUrl() : ds.getBaseUrl();
        return new DashScopeEmbeddingClient(apiKey, baseUrl, emb.getModel(), emb.getDimensions(),
                emb.getBatchSize(), emb.getTimeout(), emb.getMaxRetries(), emb.isLogRequests());
    }

    /**
     * 知识库向量索引：进程内检索 + MySQL 持久化。
     *
     * <p>Bean 存在与否就是"语义检索是否可用"的唯一判据——
     * 业务侧一律通过 {@code ObjectProvider} 取用，取不到就走原来的 2-gram 关键词检索，
     * 因此不存在"向量库故障导致对话报错"的可能。
     */
    @Bean
    @ConditionalOnExpression(EMBEDDING_ENABLED)
    public KbVectorIndex kbVectorIndex(KbDocumentMapper kbDocumentMapper,
                                       KbEmbeddingMapper kbEmbeddingMapper,
                                       EmbeddingClient embeddingClient,
                                       AiProperties properties,
                                       ObjectMapper objectMapper) {
        return new KbVectorIndex(kbDocumentMapper, kbEmbeddingMapper, embeddingClient,
                properties.getEmbedding(), objectMapper);
    }

    // ==================== 天气查询 ====================

    /**
     * 天气数据源（和风天气）。
     *
     * <p>与向量检索一样是<b>可缺席</b>的 Bean：没配 API Host 就不创建。
     * 上层用 {@code ObjectProvider} 取用，取不到时天气工具会如实说"未配置"，
     * 而不是抛异常把对话打断。
     */
    @Bean
    @ConditionalOnExpression(WEATHER_ENABLED)
    public WeatherClient weatherClient(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Weather w = properties.getWeather();
        log.info("天气查询已启用：provider=和风天气，host={}，location={}，缓存 {} 分钟",
                w.getHost(), w.getLocation(), w.getCacheTtl().toMinutes());
        return new QWeatherClient(w, objectMapper);
    }
}
