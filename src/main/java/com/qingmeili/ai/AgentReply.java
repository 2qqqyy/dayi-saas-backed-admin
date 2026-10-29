package com.qingmeili.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 山野向导回答的语义结构：正文 + 分类 + 是否解决 + 推荐卡片引用。
 *
 * <p>由 {@link AgentMeta} 从模型输出的 {@code %%META%%} 标记行解析而来，
 * 因此这里不再承担「给模型描述字段含义」的职责，只是一个内部强类型载体。
 */
@Data
public class AgentReply {

    /** 给游客的回答正文（已剥离元信息行） */
    private String content = "";

    /** 问题分类，取值见 {@code AgentMeta} 的分类枚举 */
    private String category = "其他";

    /** 是否已解决；false 表示需要人工跟进 */
    private Boolean resolved = Boolean.TRUE;

    /** 推荐卡片引用，形如 {@code homestay:1}；由业务侧回表校验后才会变成卡片 */
    private List<String> cards = new ArrayList<>();
}
