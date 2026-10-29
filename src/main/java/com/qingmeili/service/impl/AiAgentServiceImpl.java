package com.qingmeili.service.impl;

import com.qingmeili.ai.AgentMeta;
import com.qingmeili.ai.AgentReply;
import com.qingmeili.ai.AiCardFactory;
import com.qingmeili.ai.AiDraft;
import com.qingmeili.ai.ContentCopyAgent;
import com.qingmeili.ai.MarketingCopy;
import com.qingmeili.ai.QingmeiliAgent;
import com.qingmeili.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 大模型 Agent 调用实现。
 *
 * <p>设计原则：这里永远不把异常抛给上层。模型限流、额度耗尽、网络抖动、返回不合规，
 * 一律记日志并返回 {@code null}，由 {@code AiServiceImpl} 决定回退到规则引擎——
 * C 端游客的对话体验不能被一次大模型故障打断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    /** 与系统提示词中的分类保持一致；模型偶发返回其他说法时统一归入「其他」 */
    private static final Set<String> CATEGORIES = Set.of(
            "行程规划", "住宿指南", "游玩指南", "乡村好物", "出行交通", "亲子出行", "其他");

    private final ObjectProvider<QingmeiliAgent> agentProvider;
    private final ObjectProvider<ContentCopyAgent> copyAgentProvider;
    private final AiCardFactory cardFactory;

    @Override
    public boolean available() {
        return agentProvider.getIfAvailable() != null;
    }

    @Override
    public AiDraft ask(String sessionId, String question, Long userId, String userName) {
        QingmeiliAgent agent = agentProvider.getIfAvailable();
        if (agent == null) {
            return null;
        }
        String memoryId = StringUtils.hasText(sessionId) ? sessionId : "anonymous";
        long startedAt = System.currentTimeMillis();
        try {
            // 把平台实时数据确定性地放进上下文，模型不调工具也不会瞎编；
            // 传入 question 是为了让知识库正文按语义"选择性"召回——只喂与本次提问相关的原文，
            // 否则模型只能看到标题、看不到内容，遇到规则类问题仍会凭常识编造细节。
            // 末尾的 %%META%% 标记行承载分类与卡片引用，避免占用 response_format（会挤掉函数调用）
            String prompt = "【平台资料·实时】\n" + cardFactory.platformDigest(question)
                    + "\n\n【游客问题】" + question;
            AgentReply reply = AgentMeta.parse(agent.answer(memoryId, prompt), question);
            if (!StringUtils.hasText(reply.getContent())) {
                log.warn("AI Agent 返回空内容，回退规则引擎：session={}", memoryId);
                return null;
            }
            AiDraft draft = new AiDraft();
            draft.setContent(reply.getContent().trim());
            draft.setCategory(normalizeCategory(reply.getCategory()));
            draft.setSource("LLM");
            draft.setResolved(Boolean.FALSE.equals(reply.getResolved()) ? 0 : 1);
            // 卡片必须回表校验：模型给出的引用可能不存在或已下架，一律过滤掉
            draft.setCards(cardFactory.resolveRefs(reply.getCards()));
            draft.setTokenUsage(AiDraft.estimateTokens(question, draft.getContent(), draft.getCards().size()));
            log.info("AI Agent 回答完成：session={}，用户={}，分类={}，卡片={} 张，耗时={}ms",
                    memoryId, StringUtils.hasText(userName) ? userName : "游客",
                    draft.getCategory(), draft.getCards().size(), System.currentTimeMillis() - startedAt);
            return draft;
        } catch (Exception e) {
            log.warn("AI Agent 调用失败，回退规则引擎：session={}，原因={}", memoryId, e.toString());
            return null;
        }
    }

    @Override
    public MarketingCopy writeCopy(String brief) {
        ContentCopyAgent agent = copyAgentProvider.getIfAvailable();
        if (agent == null || !StringUtils.hasText(brief)) {
            return null;
        }
        try {
            MarketingCopy copy = agent.writeCopy(brief);
            if (copy == null || !StringUtils.hasText(copy.getCopy())) {
                return null;
            }
            return copy;
        } catch (Exception e) {
            log.warn("AI Agent 文案生成失败，回退模板文案：{}", e.toString());
            return null;
        }
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "其他";
        }
        String value = category.trim();
        return CATEGORIES.contains(value) ? value : "其他";
    }
}
