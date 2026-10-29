package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI 问答会话记录（C端每一次提问落一条，后台按 session 聚合展示）
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String userName;

    private String question;

    private String answer;

    private String category;

    private Long kbDocumentId;

    /** KB 命中知识库 / RULE 规则兜底 */
    private String source;

    private Integer resolved;

    private Integer transferred;

    private Integer tokenUsage;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
