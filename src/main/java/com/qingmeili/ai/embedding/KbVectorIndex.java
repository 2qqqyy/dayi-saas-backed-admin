package com.qingmeili.ai.embedding;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingmeili.ai.AiCardFactory;
import com.qingmeili.ai.AiProperties;
import com.qingmeili.entity.KbDocument;
import com.qingmeili.entity.KbEmbedding;
import com.qingmeili.mapper.KbDocumentMapper;
import com.qingmeili.mapper.KbEmbeddingMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 知识库向量索引（RAG 的检索引擎）。
 *
 * <p><b>为什么是"进程内向量库 + MySQL 持久化"，而不是 Milvus / Qdrant / Redis-Stack：</b>
 * 本项目是"解压即用"的便携版课程项目，多一个向量数据库就意味着多一份 Docker 依赖、
 * 多一段部署文档、多一个演示当天可能起不来的环节。当前知识库只有几十条、正文几十到几百字，
 * 全量向量常驻内存后，一次检索是几十次浮点乘加的毫秒级操作——
 * 引入独立向量库带来的运维成本，远大于它省下的那点查询时间。
 * 检索能力被抽象在 {@link #search} 这一个方法里，将来数据涨到几万条时，
 * 把它换成 Milvus/Qdrant 的调用即可，上层的工具与规则引擎都不用改。
 *
 * <p><b>索引的维护方式：</b>
 * <ul>
 *   <li>启动时异步做一次增量同步：只重算"版本号变了 / 向量缺失 / 换了嵌入模型"的文档，
 *       其余直接复用库里的向量，重启不产生任何 API 调用；</li>
 *   <li>后台增删改知识库后，{@link #indexDocumentAsync} 立即重建该文档的向量，做到"改完即生效"；</li>
 *   <li>全文替换用写时复制：检索读旧快照，写入完成后整体替换引用，检索线程永不被阻塞。</li>
 * </ul>
 *
 * <p><b>降级契约（很重要）：</b>本类所有对外方法都不会把异常抛给业务层。
 * 嵌入服务不可用、额度耗尽、表还没建，一律记日志并返回空结果，
 * 由调用方（{@link com.qingmeili.ai.AiAgentTools}、{@code AiServiceImpl}）
 * 回落到原有的 2-gram 关键词检索——C 端游客的对话不会因为向量库故障而中断。
 */
@Slf4j
public class KbVectorIndex {

    /**
     * 关键词分的加成权重。
     *
     * <p>这里是<b>加成</b>而不是加权平均——这个取舍来自实测：
     * 若用 {@code 0.8*语义 + 0.2*字面} 的加权平均，一条语义分很高但字面完全不重合的文档
     * （口语化提问几乎必然如此）会被拉低，反而输给"语义平平但踩中两个常用词"的文档。
     * 实测问「我临时去不了了怎么办」时，真正的答案「下雨天可以取消采摘吗」
     * 语义分 0.6148 全场最高，却因为字面分只有 0.1 被压到第 3 位。
     * 改成"语义为主 + 字面小加成"后，语义最高的文档稳定胜出，
     * 同时字面重合仍然能起到锦上添花的排位作用。
     */
    private static final double KEYWORD_BONUS = 0.12;
    /** 融合分上限 */
    private static final double SCORE_CAP = 0.99;
    /** 标题完整命中的保底分：字面证据已经确定，不该被"语义上稍像别的文档"挤下去 */
    private static final double TITLE_HIT_FLOOR = 0.95;
    /** 关键词原始分的归一化基准 */
    private static final double KEYWORD_NORM = 10.0;
    /** 查询向量缓存条数：同一问题在 Agent 多轮工具调用里会被反复编码，缓存能省掉重复计费 */
    private static final int QUERY_CACHE_MAX = 256;
    /** 可见文档列表的缓存时长，避免每次检索都查库 */
    private static final long DOC_CACHE_TTL_MS = 5_000L;
    /** 单个分片入库的最大字数，与 kb_embedding.content 的 VARCHAR(1000) 对齐 */
    private static final int MAX_CHUNK_STORE_LEN = 1000;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 内存中的向量分片 */
    private record CachedChunk(long docId, int chunkIndex, String chunkType, String content, float[] vector) {
    }

    /** 待写入的分片（文档 + 分片配对） */
    private record Pending(KbDocument doc, TextChunk part) {
    }

    private final KbDocumentMapper docMapper;
    private final KbEmbeddingMapper embMapper;
    private final EmbeddingClient client;
    private final AiProperties.Embedding cfg;
    private final ObjectMapper json;

    /** 索引快照：整体替换，检索读旧版本、写入换新版本，互不阻塞 */
    private volatile List<CachedChunk> chunks = List.of();
    private volatile long lastReloadAt = 0L;
    private volatile long lastRebuildAt = 0L;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicBoolean loadFailureLogged = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    /** 可见文档短缓存 */
    private volatile List<KbDocument> docCache = List.of();
    private volatile long docCacheAt = 0L;

    /** 查询向量 LRU（按访问顺序淘汰） */
    private final Map<String, float[]> queryCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > QUERY_CACHE_MAX;
                }
            });

    /** 单线程执行器：向量化是网络 IO，串行执行避免打爆嵌入服务的并发限制 */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kb-vector-index");
        thread.setDaemon(true);
        return thread;
    });

    public KbVectorIndex(KbDocumentMapper docMapper,
                         KbEmbeddingMapper embMapper,
                         EmbeddingClient client,
                         AiProperties.Embedding cfg,
                         ObjectMapper json) {
        this.docMapper = docMapper;
        this.embMapper = embMapper;
        this.client = client;
        this.cfg = cfg;
        this.json = json;
    }

    public boolean available() {
        return client.available();
    }

    public String model() {
        return client.model();
    }

    // ==================== 检索 ====================

    /**
     * 语义检索。
     *
     * @param query    游客原话
     * @param topK     最多返回几条
     * @param minScore 低于该分视为未命中（返回空列表，由调用方决定是否降级）
     * @return 按相关度倒序的命中列表；任何异常都返回空列表
     */
    public List<VectorHit> search(String query, int topK, double minScore) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        ensureLoaded();
        List<CachedChunk> snapshot = this.chunks;
        if (snapshot.isEmpty()) {
            return List.of();
        }

        float[] queryVector;
        try {
            queryVector = queryVector(query);
        } catch (Exception e) {
            log.warn("查询向量化失败，本次检索降级为关键词匹配：{}", e.toString());
            return List.of();
        }

        // 1) 逐分片算余弦，同一个文档只保留得分最高的那个分片
        Map<Long, Double> bestVector = new HashMap<>();
        Map<Long, CachedChunk> bestChunk = new HashMap<>();
        for (CachedChunk chunk : snapshot) {
            if (chunk.vector().length != queryVector.length) {
                continue;
            }
            double similarity = cosine(queryVector, chunk.vector());
            Double current = bestVector.get(chunk.docId());
            if (current == null || similarity > current) {
                bestVector.put(chunk.docId(), similarity);
                bestChunk.put(chunk.docId(), chunk);
            }
        }
        if (bestVector.isEmpty()) {
            return List.of();
        }

        // 2) 语义分与关键词分融合
        List<VectorHit> hits = new ArrayList<>();
        for (KbDocument doc : visibleDocuments()) {
            Double vectorScore = bestVector.get(doc.getId());
            if (vectorScore == null) {
                continue;
            }
            String title = AiCardFactory.nz(doc.getTitle());
            boolean titleHit = title.length() > 1 && query.contains(title);
            double keywordScore = keywordScore(query, doc, titleHit);
            // 语义为主、字面加分，理由见 KEYWORD_BONUS 的注释
            double score = Math.min(vectorScore + KEYWORD_BONUS * keywordScore, SCORE_CAP);
            if (titleHit) {
                score = Math.max(score, TITLE_HIT_FLOOR);
            }
            CachedChunk chunk = bestChunk.get(doc.getId());
            hits.add(new VectorHit(doc.getId(), title, AiCardFactory.nz(doc.getCategory()),
                    AiCardFactory.nz(doc.getContent()), score, vectorScore, keywordScore, titleHit,
                    chunk == null ? "" : chunk.content(),
                    chunk == null ? "" : chunk.chunkType()));
        }
        hits.sort(Comparator.comparingDouble(VectorHit::score).reversed());
        return hits.stream()
                .filter(hit -> hit.score() >= minScore)
                .limit(Math.max(topK, 1))
                .collect(Collectors.toList());
    }

    /** 用默认配置检索 */
    public List<VectorHit> search(String query) {
        return search(query, cfg.getTopK(), cfg.getMinScore());
    }

    private float[] queryVector(String query) {
        String key = client.model() + "|" + query.trim();
        float[] cached = queryCache.get(key);
        if (cached != null) {
            return cached;
        }
        float[] vector = client.embed(List.of(query.trim()))[0];
        queryCache.put(key, vector);
        return vector;
    }

    /** 关键词分：沿用原先的 2-gram 加权，归一化到 0-1 后与语义分融合 */
    private double keywordScore(String query, KbDocument doc, boolean titleHit) {
        Set<String> queryGrams = AiCardFactory.ngrams(query);
        int raw = (titleHit ? 8 : 0)
                + AiCardFactory.overlap(queryGrams, AiCardFactory.ngrams(AiCardFactory.nz(doc.getTitle()))) * 3
                + AiCardFactory.overlap(queryGrams, AiCardFactory.ngrams(AiCardFactory.nz(doc.getCategory()))) * 2
                + Math.min(AiCardFactory.overlap(queryGrams, AiCardFactory.ngrams(AiCardFactory.nz(doc.getContent()))), 3);
        return Math.min(raw / KEYWORD_NORM, 1.0);
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 索引维护 ====================

    /** 重建单个文档的向量（幂等），返回是否存在有效向量 */
    public boolean indexDocument(long docId) {
        KbDocument doc = docMapper.selectById(docId);
        if (doc == null || !visible(doc)) {
            removeDocument(docId);
            return false;
        }
        try {
            synchronized (writeLock) {
                writeDocs(List.of(doc));
            }
        } catch (Exception e) {
            log.warn("文档向量化失败：docId={}，原因={}", docId, e.toString());
            return false;
        }
        invalidateDocCache();
        reloadFromDb();
        return true;
    }

    /** 异步重建单个文档，供后台增删改后调用，不阻塞接口响应 */
    public void indexDocumentAsync(long docId) {
        worker.submit(() -> {
            try {
                indexDocument(docId);
            } catch (Exception e) {
                log.warn("异步向量化失败：docId={}，原因={}", docId, e.toString());
            }
        });
    }

    /** 删除某文档的全部向量 */
    public void removeDocument(long docId) {
        try {
            synchronized (writeLock) {
                embMapper.delete(new LambdaQueryWrapper<KbEmbedding>().eq(KbEmbedding::getDocId, docId));
            }
        } catch (Exception e) {
            log.warn("删除文档向量失败：docId={}，原因={}", docId, e.toString());
        }
        invalidateDocCache();
        reloadFromDb();
    }

    /**
     * 全量重建：清空向量表，按当前可见文档重新生成。
     * 返回统计信息，供后台展示。
     */
    public Map<String, Object> rebuildAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        long startedAt = System.currentTimeMillis();
        List<KbDocument> docs;
        try {
            docs = visibleDocuments();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "读取知识库失败，请确认已执行 数据库脚本/16-知识库向量化.sql：" + e);
            return result;
        }
        if (docs.isEmpty()) {
            result.put("success", true);
            result.put("documents", 0);
            result.put("chunks", 0);
            result.put("elapsedMs", System.currentTimeMillis() - startedAt);
            result.put("message", "知识库暂无启用且审核通过的文档，无需重建");
            return result;
        }
        int chunkCount = 0;
        int failed = 0;
        synchronized (writeLock) {
            try {
                embMapper.delete(new LambdaQueryWrapper<KbEmbedding>().gt(KbEmbedding::getId, 0));
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "清空向量表失败，请确认已执行 数据库脚本/16-知识库向量化.sql：" + e);
                return result;
            }
            for (KbDocument doc : docs) {
                try {
                    chunkCount += writeDocs(List.of(doc));
                } catch (Exception e) {
                    failed++;
                    log.warn("全量重建时文档向量化失败：docId={}，原因={}", doc.getId(), e.toString());
                }
            }
        }
        invalidateDocCache();
        reloadFromDb();
        lastRebuildAt = System.currentTimeMillis();
        result.put("success", failed == 0);
        result.put("documents", docs.size());
        result.put("indexedDocuments", docs.size() - failed);
        result.put("chunks", chunkCount);
        result.put("failed", failed);
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
        result.put("message", failed == 0
                ? "向量重建完成：" + docs.size() + " 篇文档、共 " + chunkCount + " 个分片"
                : "部分文档向量化失败：" + failed + " 篇（多为嵌入服务限流或超时，可重试）");
        log.info("知识库向量全量重建完成：文档={}，分片={}，失败={}，耗时={}ms",
                docs.size(), chunkCount, failed, System.currentTimeMillis() - startedAt);
        return result;
    }

    /** 把若干文档的所有分片一次性向量化并入库（嵌入客户端内部按 10 条一批） */
    private int writeDocs(List<KbDocument> docs) {
        List<Pending> pending = new ArrayList<>();
        for (KbDocument doc : docs) {
            for (TextChunk part : ChunkSplitter.split(doc, cfg.getChunkSize(), cfg.getChunkOverlap())) {
                pending.add(new Pending(doc, part));
            }
        }
        if (pending.isEmpty()) {
            return 0;
        }
        float[][] vectors = client.embed(pending.stream().map(item -> item.part().content()).toList());
        if (vectors.length != pending.size()) {
            throw new IllegalStateException("向量数量与分片数量不一致：分片 " + pending.size() + "，向量 " + vectors.length);
        }
        List<Long> docIds = docs.stream().map(KbDocument::getId).filter(java.util.Objects::nonNull).toList();
        if (!docIds.isEmpty()) {
            embMapper.delete(new LambdaQueryWrapper<KbEmbedding>().in(KbEmbedding::getDocId, docIds));
        }
        for (int i = 0; i < pending.size(); i++) {
            Pending item = pending.get(i);
            KbEmbedding row = new KbEmbedding();
            row.setDocId(item.doc().getId());
            row.setChunkIndex(item.part().index());
            row.setChunkType(item.part().type());
            row.setContent(truncate(item.part().content()));
            row.setEmbedding(toJson(vectors[i]));
            row.setDim(vectors[i].length);
            row.setModel(client.model());
            row.setDocVersion(item.doc().getVersion() == null ? 1 : item.doc().getVersion());
            embMapper.insert(row);
        }
        return pending.size();
    }

    // ==================== 加载与同步 ====================

    /** 首次检索前把库里的向量载入内存 */
    private void ensureLoaded() {
        if (loaded.get()) {
            return;
        }
        synchronized (this) {
            if (loaded.get()) {
                return;
            }
            try {
                reloadFromDb();
                loaded.set(true);
            } catch (Exception e) {
                // 表不存在或库不可用：只提醒一次，之后走关键词降级，避免刷日志
                if (loadFailureLogged.compareAndSet(false, true)) {
                    log.warn("向量索引载入失败，检索将降级为关键词匹配"
                            + "（请确认已执行 数据库脚本/16-知识库向量化.sql）：{}", e.toString());
                }
                loaded.set(true);
                chunks = List.of();
            }
        }
    }

    /** 从表中重新载入全部向量，并整体替换内存快照 */
    private void reloadFromDb() {
        List<KbEmbedding> rows = embMapper.selectList(new LambdaQueryWrapper<KbEmbedding>()
                .orderByAsc(KbEmbedding::getDocId)
                .orderByAsc(KbEmbedding::getChunkIndex));
        List<CachedChunk> loadedChunks = new ArrayList<>(rows.size());
        for (KbEmbedding row : rows) {
            float[] vector = parseVector(row.getEmbedding());
            if (vector.length == 0) {
                continue;
            }
            loadedChunks.add(new CachedChunk(row.getDocId(), row.getChunkIndex() == null ? 0 : row.getChunkIndex(),
                    row.getChunkType(), row.getContent(), vector));
        }
        this.chunks = List.copyOf(loadedChunks);
        this.lastReloadAt = System.currentTimeMillis();
    }

    /**
     * 增量同步：只重算"版本号变了 / 向量缺失 / 换了嵌入模型"的文档，并清掉已不可见文档的残留向量。
     * 重启时走这条路，正常情况下不产生任何 API 调用。
     */
    private void syncIncremental() {
        List<KbDocument> docs = visibleDocuments();
        Map<Long, Integer> storedVersion = new HashMap<>();
        Set<Long> storedDocs = new HashSet<>();
        Set<Long> staleModel = new HashSet<>();
        for (KbEmbedding row : embMapper.selectList(new LambdaQueryWrapper<KbEmbedding>())) {
            storedVersion.put(row.getDocId(), row.getDocVersion());
            storedDocs.add(row.getDocId());
            if (!client.model().equals(row.getModel())) {
                staleModel.add(row.getDocId());
            }
        }
        List<KbDocument> stale = new ArrayList<>();
        Set<Long> visibleIds = new HashSet<>();
        for (KbDocument doc : docs) {
            visibleIds.add(doc.getId());
            Integer version = storedVersion.get(doc.getId());
            int current = doc.getVersion() == null ? 1 : doc.getVersion();
            if (version == null || version != current || staleModel.contains(doc.getId())) {
                stale.add(doc);
            }
        }
        List<Long> orphans = storedDocs.stream().filter(id -> !visibleIds.contains(id)).toList();

        if (stale.isEmpty() && orphans.isEmpty()) {
            log.info("知识库向量索引检查完成：{} 篇文档、{} 个分片全部为最新，无需重新向量化", docs.size(), chunks.size());
            return;
        }
        if (!orphans.isEmpty()) {
            embMapper.delete(new LambdaQueryWrapper<KbEmbedding>().in(KbEmbedding::getDocId, orphans));
            log.info("清理 {} 篇已下架/已删除文档的残留向量", orphans.size());
        }
        if (!stale.isEmpty()) {
            long startedAt = System.currentTimeMillis();
            int chunkCount = 0;
            int failed = 0;
            synchronized (writeLock) {
                for (KbDocument doc : stale) {
                    try {
                        chunkCount += writeDocs(List.of(doc));
                    } catch (Exception e) {
                        failed++;
                        log.warn("增量向量化失败：docId={}，原因={}", doc.getId(), e.toString());
                    }
                }
            }
            log.info("知识库向量增量同步完成：新增/更新 {} 篇（分片 {} 个，失败 {} 篇），耗时 {}ms",
                    stale.size(), chunkCount, failed, System.currentTimeMillis() - startedAt);
        }
        invalidateDocCache();
        reloadFromDb();
    }

    /**
     * 启动后异步预热：加载内存索引并做一次增量同步。
     * 放在 ApplicationReadyEvent 之后、独立线程里执行，不拖慢服务启动，
     * 即使嵌入服务当时不可用也只影响检索质量，不影响任何业务接口。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        worker.submit(() -> {
            try {
                ensureLoaded();
                if (cfg.isSyncOnStartup()) {
                    syncIncremental();
                } else {
                    log.info("已跳过知识库向量增量同步（ai.embedding.sync-on-startup=false），当前分片数 {}", chunks.size());
                }
            } catch (Exception e) {
                log.warn("知识库向量索引预热失败，检索将降级为关键词匹配：{}", e.toString());
            }
        });
    }

    // ==================== 状态与工具方法 ====================

    /** 向量库运行状态，供后台展示 */
    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", true);
        map.put("model", client.model());
        map.put("dimensions", client.dimensions());
        map.put("topK", cfg.getTopK());
        map.put("minScore", cfg.getMinScore());
        map.put("ruleMatchScore", cfg.getRuleMatchScore());
        map.put("chunkSize", cfg.getChunkSize());
        map.put("chunkOverlap", cfg.getChunkOverlap());
        map.put("syncOnStartup", cfg.isSyncOnStartup());
        map.put("chunks", chunks.size());
        map.put("indexedDocuments", chunks.stream().map(CachedChunk::docId).distinct().count());
        map.put("lastReloadAt", lastReloadAt == 0 ? null : LocalDateTime.now().minusNanos(0).format(TIME) == null
                ? null : formatTime(lastReloadAt));
        map.put("lastRebuildAt", lastRebuildAt == 0 ? null : formatTime(lastRebuildAt));
        map.put("queryCacheSize", queryCache.size());
        try {
            List<KbDocument> docs = visibleDocuments();
            List<Long> pending = staleDocIds(docs);
            map.put("tableReady", true);
            map.put("documents", docs.size());
            map.put("pendingDocuments", pending.size());
            map.put("pendingDocIds", pending);
        } catch (Exception e) {
            map.put("tableReady", false);
            map.put("documents", 0);
            map.put("pendingDocuments", 0);
            map.put("error", e.toString());
        }
        return map;
    }

    /** 待向量化的文档 id：向量缺失、版本号落后、或嵌入模型已更换 */
    private List<Long> staleDocIds(List<KbDocument> docs) {
        Map<Long, Integer> storedVersion = new HashMap<>();
        Set<Long> staleModel = new HashSet<>();
        for (KbEmbedding row : embMapper.selectList(new LambdaQueryWrapper<KbEmbedding>())) {
            storedVersion.put(row.getDocId(), row.getDocVersion());
            if (!client.model().equals(row.getModel())) {
                staleModel.add(row.getDocId());
            }
        }
        List<Long> stale = new ArrayList<>();
        for (KbDocument doc : docs) {
            Integer version = storedVersion.get(doc.getId());
            int current = doc.getVersion() == null ? 1 : doc.getVersion();
            if (version == null || version != current || staleModel.contains(doc.getId())) {
                stale.add(doc.getId());
            }
        }
        return stale;
    }

    /** 全部可见文档（启用 + 审核通过），带 5 秒短缓存 */
    private List<KbDocument> visibleDocuments() {
        List<KbDocument> cached = docCache;
        if (!cached.isEmpty() && System.currentTimeMillis() - docCacheAt < DOC_CACHE_TTL_MS) {
            return cached;
        }
        List<KbDocument> docs = docMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, 1)
                .eq(KbDocument::getAuditStatus, 1));
        docCache = docs;
        docCacheAt = System.currentTimeMillis();
        return docs;
    }

    private void invalidateDocCache() {
        docCacheAt = 0L;
        docCache = List.of();
    }

    private static boolean visible(KbDocument doc) {
        return doc.getStatus() != null && doc.getStatus() == 1
                && doc.getAuditStatus() != null && doc.getAuditStatus() == 1;
    }

    /** 向量序列化：固定 6 位小数，兼顾精度与可读性（1024 维约 10KB） */
    private static String toJson(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        return sb.append(']').toString();
    }

    private float[] parseVector(String text) {
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }
        try {
            return json.readValue(text, float[].class);
        } catch (Exception e) {
            log.warn("向量反序列化失败，已跳过该分片：{}", e.toString());
            return new float[0];
        }
    }

    private static String truncate(String text) {
        String value = text == null ? "" : text;
        return value.length() <= MAX_CHUNK_STORE_LEN ? value : value.substring(0, MAX_CHUNK_STORE_LEN);
    }

    private static String formatTime(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault())
                .format(TIME);
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }

    /** 供后台查看：当前内存索引里每篇文档的分片数 */
    public Map<Long, Long> chunkCountByDocument() {
        return chunks.stream().collect(Collectors.groupingBy(CachedChunk::docId, LinkedHashMap::new, Collectors.counting()));
    }
}
