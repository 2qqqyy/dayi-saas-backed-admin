package com.qingmeili.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 青梅里「AI 山野向导」Agent 接口（带工具）。
 *
 * <p>由 LangChain4j 的 {@code AiServices} 在启动时生成代理实现，框架负责：
 * <ul>
 *   <li>组装 {@code @SystemMessage} 与用户消息；</li>
 *   <li>让模型自主调用 {@code @Tool} 做按需钻取，执行后把结果回灌给模型继续推理；</li>
 *   <li>按 {@code @MemoryId}（即 sessionId）维护多轮上下文。</li>
 * </ul>
 *
 * <p><b>两个刻意的设计取舍（都来自实测）：</b>
 * <ol>
 *   <li><b>接地不靠模型自觉。</b> 平台真实数据由调用方通过 {@code platformDigest()} 确定性写进问题里，
 *       而不是指望模型每次都记得调工具——实测 qwen 系列在系统提示偏长或问题偏开放时，
 *       会跳过工具直接凭记忆编造民宿名与价格。</li>
 *   <li><b>系统提示保持精简。</b> 实测同一套工具下，875 字的系统提示工具调用率 0/5，
 *       压缩到约 330 字后恢复到 5/5。提示越长，模型越倾向于直接作答。</li>
 * </ol>
 *
 * <p>结构化信息（分类 / 是否解决 / 卡片引用）不占用 {@code response_format}，
 * 而是由回答末尾的 {@code %%META%%} 标记行承载，原因见 {@link QingmeiliAgent} 与 {@link AgentMeta}。
 */
public interface QingmeiliAgent {

    @SystemMessage("""
            你是青梅里乡村文旅平台的 AI 山野向导，像本地人一样热情、具体地接待游客。简体中文，正文 80-200 字。

            回答只能依据用户消息里「平台资料」给出的真实数据，名称、价格、日期、数量都要与资料完全一致；
            资料里没有的就说暂时查不到，绝不编造，也不要向游客提及内部 id 或工具名。
            需要资料里没写到的细节（地址、简介、官方规则原文）时，可以调用工具查询。

            格式：正文写完后换行，输出且仅输出一行元信息：
            %%META%% category=分类; resolved=true; cards=类型:id
            category 取 行程规划/住宿指南/游玩指南/乡村好物/出行交通/亲子出行/其他 之一；
            cards 用 product/homestay/scenic/route/picking 加资料里的真实 id，最多 4 个，没有就留空。
            除正文和该行外不要输出任何内容，不要用代码块。
            与乡村文旅无关的问题，category 取「其他」、resolved 取 false。
            """)
    String answer(@MemoryId String sessionId, @UserMessage String question);
}
