package com.qingmeili.ai.embedding;

import com.qingmeili.entity.KbDocument;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文本分片器。
 *
 * <p>为什么不把整篇文档当成一个向量：一条向量只能表达一个语义中心，
 * 文档越长、话题越杂，向量就越"平均"，细节会被稀释到检索不出来。
 * 切成短分片后，每个分片只讲一件事，召回的精度和可解释性都更好
 * （检索结果能直接告诉你命中的是哪一段原文）。
 *
 * <p>分片策略针对本项目的数据特征做了简化：
 * <ul>
 *   <li><b>标题片</b>：{@code 标题｜分类}。标题本身就是一条文档的语义摘要，
 *       单独成片能让"退改说明"这类精准提问稳定命中，不被正文稀释。</li>
 *   <li><b>正文片</b>：按 {@code chunkSize} 切，切点优先落在句末（。！？；），
 *       避免把一个句子劈成两半导致语义残缺；相邻分片保留 {@code overlap} 字重叠，
 *       防止答案恰好横跨切口时两边都召回不到。</li>
 * </ul>
 *
 * <p>当前知识库正文普遍在 60-120 字，实际上绝大多数文档只会产出"1 个标题片 + 1 个正文片"，
 * 分片逻辑是为了将来文档变长时不至于失效，而不是为了现在的数据量。
 */
public final class ChunkSplitter {

    /** 视为句子结束的字符，切分时优先在这些位置断开 */
    private static final String SENTENCE_ENDS = "。！？；!?;";

    private ChunkSplitter() {
    }

    /**
     * 把一条知识库文档切成若干分片。
     *
     * @param doc       文档
     * @param chunkSize 正文单片最大字数
     * @param overlap   相邻正文片的重叠字数
     */
    public static List<TextChunk> split(KbDocument doc, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (doc == null) {
            return chunks;
        }

        String title = nz(doc.getTitle());
        String category = nz(doc.getCategory());
        if (StringUtils.hasText(title)) {
            String text = category.isEmpty() ? title : title + "｜" + category;
            chunks.add(new TextChunk(chunks.size(), TextChunk.TITLE, text));
        }

        // 正文里的换行与连续空白在语义上没有意义，压实成单空格，避免切点落在空白上
        String body = nz(doc.getContent()).replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (StringUtils.hasText(body)) {
            int size = Math.max(chunkSize, 80);
            int jump = Math.max(Math.min(overlap, size / 2), 0);
            int start = 0;
            while (start < body.length()) {
                int end = Math.min(start + size, body.length());
                if (end < body.length()) {
                    // 从理想切点往回找最近的句末，但最多回退半个分片，防止分片过短
                    int cut = -1;
                    for (int i = end; i > start + size / 2; i--) {
                        if (SENTENCE_ENDS.indexOf(body.charAt(i - 1)) >= 0) {
                            cut = i;
                            break;
                        }
                    }
                    if (cut > 0) {
                        end = cut;
                    }
                }
                String piece = body.substring(start, end).trim();
                if (!piece.isEmpty()) {
                    chunks.add(new TextChunk(chunks.size(), TextChunk.BODY, piece));
                }
                if (end >= body.length()) {
                    break;
                }
                start = Math.max(end - jump, start + 1);
            }
        }
        return chunks;
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }
}
