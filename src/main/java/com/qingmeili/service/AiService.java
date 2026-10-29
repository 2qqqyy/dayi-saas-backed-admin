package com.qingmeili.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.qingmeili.common.result.Result;

import java.util.List;
import java.util.Map;

/**
 * AI 山野向导：知识库优先问答 + 行程生成 + 运营文案生成，全部基于库内真实数据并留痕
 */
public interface AiService {

    /** C端问答：命中知识库优先，未命中走规则引擎，问答记录写入 ai_conversation */
    Result<JsonNode> chat(String sessionId, String question, Long userId, String userName);

    /** C端行程生成：基于 travel_route / orchard / scenic / homestay 真实数据，写入 ai_itinerary */
    Result<JsonNode> itinerary(String date, Integer people, String type, Long userId, String userName);

    /** C端保存行程：标记 ai_itinerary.saved，后台行程列表可见 */
    Result<Void> saveItinerary(Long id, Long userId);

    /** C端推荐问题：取启用状态的知识库标题，运营在后台新增知识即可影响 C 端 */
    Result<List<String>> suggestions();

    /** 后台内容生成：按主题结合库内景点/商品/路线生成活动文案 */
    Result<Map<String, Object>> generateContent(String topic);
}
