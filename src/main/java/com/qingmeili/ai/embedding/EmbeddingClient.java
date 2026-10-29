package com.qingmeili.ai.embedding;

/**
 * 文本嵌入客户端。
 *
 * <p>抽象成一个接口，是因为"用哪家嵌入模型"和"向量存到哪里"是两件独立的事：
 * 今天用百炼的 text-embedding-v3，将来换成别的服务只需换实现类；
 * 检索层只依赖本接口，不感知 HTTP、鉴权与批量限制。
 *
 * <p>实现必须保证：<b>返回顺序与入参顺序严格一致</b>，
 * 否则向量的归属会串行错位，检索结果会指向完全无关的文档。
 */
public interface EmbeddingClient {

    /** 当前客户端是否可用（未配置 key 时为 false，调用方据此降级到关键词检索） */
    boolean available();

    /** 嵌入模型名，用于向量表记录与换模型后的失效判断 */
    String model();

    /** 向量维度 */
    int dimensions();

    /**
     * 批量向量化。
     *
     * @param texts 待向量化文本，不可为空串（空串会被服务端拒绝）
     * @return 与入参一一对应的向量
     */
    float[][] embed(java.util.List<String> texts);
}
