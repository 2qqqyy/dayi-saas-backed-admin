package com.qingmeili.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次山野向导回答的中间结果：落库（ai_conversation）与出参（/api/ai/chat）共用同一份数据。
 *
 * <p>无论是知识库命中、大模型 Agent 生成还是规则引擎兜底，最终都归一成这个结构，
 * 保证三条链路对前端、对后台统计呈现一致的口径。
 */
@Data
public class AiDraft {

    /** 回答正文 */
    private String content = "";

    /** 分类，用于后台「问题分类分布」统计 */
    private String category = "其他";

    /** 回答来源：KB=知识库命中，LLM=大模型 Agent，RULE=规则引擎兜底 */
    private String source = "RULE";

    /** 命中的知识库条目 id，仅 source=KB 时有值 */
    private Long kbDocumentId;

    /** 是否已解决：1=是，0=否（记为转人工） */
    private int resolved = 1;

    /** 估算 token 消耗，用于后台用量统计 */
    private int tokenUsage;

    /** 推荐卡片（已校验过真实存在），最多 4 张 */
    private List<ObjectNode> cards = new ArrayList<>();

    /**
     * 粗略估算一次问答的 token 消耗，用于后台用量统计。
     * 中文大约 1.5 字符/token，卡片按其标题与简介折算，量级正确即可，不追求精确计费。
     */
    public static int estimateTokens(String question, String answer, int cardCount) {
        int chars = (question == null ? 0 : question.length())
                + (answer == null ? 0 : answer.length())
                + cardCount * 20;
        return (int) Math.ceil(chars / 1.5);
    }
}
