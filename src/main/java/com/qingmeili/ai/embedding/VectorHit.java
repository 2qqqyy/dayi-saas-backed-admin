package com.qingmeili.ai.embedding;

/**
 * 一次语义检索的命中结果。
 *
 * <p>刻意把三个分数分开暴露，而不是只给一个总分：
 * <ul>
 *   <li>{@code vectorScore}：问题与文档的余弦相似度，代表"语义像不像"；</li>
 *   <li>{@code keywordScore}：2-gram 字面重合度，代表"字面上有没有踩中"；</li>
 *   <li>{@code score}：两者融合后的排序分，同时也用于阈值过滤。</li>
 * </ul>
 * 后台的"语义检索自测"会把这几个值直接展示出来——
 * 排查"为什么这句话没召回"时，是"语义没认出"还是"字面没踩中"，一眼就能分辨。
 *
 * @param docId        命中的知识库文档 id
 * @param title        文档标题
 * @param category     文档分类
 * @param content      文档正文
 * @param score        融合总分（排序与阈值都看它）
 * @param vectorScore  余弦相似度
 * @param keywordScore 归一化后的关键词分
 * @param titleHit     问题里是否完整出现了该文档标题（最高置信的字面证据）
 * @param matchedChunk 实际命中的分片原文——检索结果可解释性的关键
 * @param matchedType  命中的分片类型：title / body
 */
public record VectorHit(long docId,
                        String title,
                        String category,
                        String content,
                        double score,
                        double vectorScore,
                        double keywordScore,
                        boolean titleHit,
                        String matchedChunk,
                        String matchedType) {

    /** 便于前端直接展示的百分制相关度 */
    public int percent() {
        return (int) Math.round(score * 100);
    }
}
