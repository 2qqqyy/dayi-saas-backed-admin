package com.qingmeili.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 后台「内容生成」Agent：把库内真实素材简报润色成一份可发布的活动文案。
 *
 * <p>与 {@link QingmeiliAgent} 分开成两个 Agent 是有意为之：
 * 这个场景不需要查库（真实素材已由调用方放进简报），但需要稳定拿到结构化结果，
 * 而百炼的 json_schema 结构化输出在「不带工具」时才是可靠的
 * （带上 tools 时模型会倾向于只吐 JSON、不调工具）。所以这里是一个不注册任何工具的 AiService。
 */
public interface ContentCopyAgent {

    @SystemMessage("""
            你是「青梅里乡村文旅平台」的内容运营，擅长写微信公众号与小红书风格的乡村旅行推广文案。

            你会收到一份包含活动主题与可引用素材（景点、商品、路线、民宿、采摘场次）的简报。
            要求：
            1. 只使用简报里出现过的名称、价格和日期，不得虚构任何素材。
            2. intro 是一句话导语，点明主题与卖点，60-90 字。
            3. copy 是正文，叙事流畅、有画面感，可包含 2-3 个具体点位或商品，150-260 字。
            4. tags 给 4-6 个标签，每个 2-6 字，不带 # 号。
            5. 简体中文，不要 Markdown 语法。
            """)
    MarketingCopy writeCopy(@UserMessage String brief);
}
