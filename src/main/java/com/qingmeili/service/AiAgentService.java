package com.qingmeili.service;

import com.qingmeili.ai.AiDraft;
import com.qingmeili.ai.MarketingCopy;

/**
 * 大模型侧能力封装：把 LangChain4j 的 Agent 包装成业务可直接调用的服务。
 *
 * <p>与 {@link AiService} 的区别：{@code AiService} 是「对外可用的向导能力」，
 * 内部按「知识库 → Agent → 规则引擎」逐级降级；本接口只负责最中间那层，
 * 且所有方法在模型不可用或调用异常时都返回 {@code null}，把降级决策权交给调用方。
 */
public interface AiAgentService {

    /** 大模型 Agent 是否可用（未配置 api-key 或已关闭时返回 false） */
    boolean available();

    /**
     * 调用山野向导 Agent 回答游客问题。
     *
     * @param sessionId 会话标识，同一会话共享上下文记忆
     * @return 归一化后的回答；Agent 不可用或调用失败返回 null
     */
    AiDraft ask(String sessionId, String question, Long userId, String userName);

    /**
     * 后台内容生成：让大模型基于库内真实素材润色活动文案。
     *
     * @param brief 包含主题与可引用素材的简报
     * @return 文案；Agent 不可用或调用失败返回 null
     */
    MarketingCopy writeCopy(String brief);
}
