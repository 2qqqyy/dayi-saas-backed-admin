package com.qingmeili.ai;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

/**
 * 后台「内容生成」的结构化输出：一份可直接发布的乡村文旅活动文案。
 */
@Data
public class MarketingCopy {

    @Description("活动导语，一句话点明主题与卖点，60-90 字")
    private String intro;

    @Description("正文推广文案，叙事流畅、有画面感，150-260 字")
    private String copy;

    @Description("标签，4-6 个，每个 2-6 字，不带 # 号")
    private List<String> tags;
}
