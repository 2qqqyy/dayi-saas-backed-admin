package com.qingmeili.ai.embedding;

/**
 * 参与向量化的文本分片。
 *
 * @param index   分片序号，同一文档内唯一（对应 kb_embedding.chunk_index）
 * @param type    分片类型，见 {@link #TITLE} / {@link #BODY}
 * @param content 送入嵌入模型的原文
 */
public record TextChunk(int index, String type, String content) {

    /** 标题片：标题 + 分类。短而"话题性强"，对意图识别贡献最大 */
    public static final String TITLE = "title";

    /** 正文片：内容按长度切分，承载细节 */
    public static final String BODY = "body";
}
