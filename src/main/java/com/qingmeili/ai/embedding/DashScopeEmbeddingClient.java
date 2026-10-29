package com.qingmeili.ai.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于阿里云百炼（DashScope）OpenAI 兼容端点的嵌入客户端。
 *
 * <p>沿用与对话模型完全相同的通道（{@code /compatible-mode/v1}），
 * 只是把 model 换成 {@code text-embedding-v3}——同一份 API Key、同一套超时与重试策略，
 * 不引入第二家服务、也不新增任何 Maven 依赖（langchain4j-open-ai 本就在项目里）。
 *
 * <p><b>两个实测得到的硬约束，代码里都做了处理：</b>
 * <ol>
 *   <li>单次请求最多 10 条文本，超过会直接返回
 *       {@code batch size is invalid, it should not be larger than 10}，
 *       所以 {@link #embed} 内部显式按 batchSize 分批，而不是依赖框架默认值。</li>
 *   <li>响应必须显式要求 {@code encoding_format=float}，否则可能拿到 base64 编码的向量。</li>
 * </ol>
 *
 * <p>失败策略：本类<b>不吞异常</b>。网络超时、额度不足、Key 失效一律抛出，
 * 由 {@link KbVectorIndex} 统一捕获并降级到关键词检索——
 * 这样"向量库挂了"只表现为检索质量下降，而不是 C 端对话报错。
 */
@Slf4j
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private final OpenAiEmbeddingModel model;
    private final String modelName;
    private final int dimensions;
    private final int batchSize;

    public DashScopeEmbeddingClient(String apiKey,
                                    String baseUrl,
                                    String modelName,
                                    int dimensions,
                                    int batchSize,
                                    Duration timeout,
                                    int maxRetries,
                                    boolean logRequests) {
        this.modelName = modelName;
        this.dimensions = dimensions;
        // 服务端上限 10，配置写大了也没用，这里兜一层
        this.batchSize = Math.max(1, Math.min(batchSize, 10));
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .dimensions(dimensions)
                .maxSegmentsPerBatch(this.batchSize)
                .encodingFormat("float")
                .timeout(timeout)
                .maxRetries(maxRetries)
                .logRequests(logRequests)
                .logResponses(false)
                .build();
        log.info("知识库向量化已启用：provider=DashScope(OpenAI 兼容端点)，embeddingModel={}，维度={}，单批={} 条",
                modelName, dimensions, this.batchSize);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String model() {
        return modelName;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[][] embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new float[0][];
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, texts.size());
            List<String> batch = texts.subList(from, to);
            EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                    .inputs(batch.stream().map(EmbeddingInput::from).toList())
                    .dimensions(dimensions)
                    .build());
            List<Embedding> embeddings = response.embeddings();
            if (embeddings == null || embeddings.size() != batch.size()) {
                throw new IllegalStateException("嵌入返回条数与请求不一致：请求 " + batch.size()
                        + " 条，返回 " + (embeddings == null ? 0 : embeddings.size()) + " 条");
            }
            for (Embedding embedding : embeddings) {
                vectors.add(embedding.vector());
            }
        }
        return vectors.toArray(new float[0][]);
    }
}
