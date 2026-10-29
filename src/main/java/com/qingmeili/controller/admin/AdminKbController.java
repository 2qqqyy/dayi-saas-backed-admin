package com.qingmeili.controller.admin;

import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.ai.embedding.VectorHit;
import com.qingmeili.common.annotation.OperationLog;
import com.qingmeili.common.annotation.RequireRole;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.KbDocument;
import com.qingmeili.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理 - 知识库文档（含向量索引维护）。
 *
 * <p>文档的增删改已自动联动向量重建，这里的 {@code /vector/*} 接口是为了两件事：
 * <ul>
 *   <li><b>可观测</b>：让运营能看到"向量库现在有多少分片、有哪些文档还没向量化"，
 *       而不是把索引状态藏在一个看不见的进程里；</li>
 *   <li><b>可干预</b>：嵌入服务限流、超时导致个别文档向量化失败时，能手动重试；
 *       换了嵌入模型后需要整体重算时，一键重建。</li>
 * </ul>
 * 另外提供语义检索自测，用于验证"换了种说法还能不能召回"。
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@RequireRole({"admin", "super_admin"})
@RequiredArgsConstructor
public class AdminKbController {

    private static final String VECTOR_DISABLED =
            "未启用向量检索：请检查 ai.embedding.enabled 是否为 true、是否配置了百炼 API Key，"
                    + "并确认已执行 数据库脚本/16-知识库向量化.sql";

    private final KbDocumentService kbDocumentService;

    /** 向量检索器：未启用时为 null，是合法状态 */
    private final ObjectProvider<KbVectorIndex> vectorIndexProvider;

    @GetMapping
    public Result<PageResult<KbDocument>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return kbDocumentService.page(page, pageSize);
    }

    @PostMapping
    @OperationLog(module = "知识库管理", action = "新增")
    public Result<KbDocument> create(@RequestBody KbDocument document) {
        return kbDocumentService.create(document);
    }

    @PutMapping("/{id}")
    @OperationLog(module = "知识库管理", action = "修改")
    public Result<KbDocument> update(@PathVariable Long id, @RequestBody KbDocument document) {
        return kbDocumentService.update(id, document);
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "知识库管理", action = "删除")
    public Result<Void> delete(@PathVariable Long id) {
        return kbDocumentService.delete(id);
    }

    // ==================== 向量索引维护 ====================

    /** 向量库运行状态：模型、维度、分片数、待向量化的文档等 */
    @GetMapping("/vector/status")
    public Result<Map<String, Object>> vectorStatus() {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        Map<String, Object> data = new LinkedHashMap<>();
        if (index == null) {
            data.put("enabled", false);
            data.put("mode", "keyword");
            data.put("message", VECTOR_DISABLED + "；当前知识库使用 2-gram 关键词检索");
            return Result.ok(data);
        }
        data.put("enabled", true);
        data.put("mode", "vector");
        data.putAll(index.status());
        return Result.ok(data);
    }

    /** 全量重建向量索引 */
    @PostMapping("/vector/rebuild")
    @OperationLog(module = "知识库管理", action = "重建向量索引")
    public Result<Map<String, Object>> rebuildVector() {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index == null) {
            return Result.fail(VECTOR_DISABLED);
        }
        Map<String, Object> result = index.rebuildAll();
        if (Boolean.FALSE.equals(result.get("success"))) {
            return Result.fail(String.valueOf(result.get("message")));
        }
        return Result.ok(String.valueOf(result.get("message")), result);
    }

    /** 重建单条文档的向量 */
    @PostMapping("/vector/{id}/sync")
    @OperationLog(module = "知识库管理", action = "同步文档向量")
    public Result<Map<String, Object>> syncVector(@PathVariable Long id) {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index == null) {
            return Result.fail(VECTOR_DISABLED);
        }
        KbDocument doc = kbDocumentService.getById(id);
        if (doc == null) {
            return Result.fail("文档不存在");
        }
        boolean indexed = index.indexDocument(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docId", id);
        data.put("indexed", indexed);
        return Result.ok(indexed
                ? "向量已更新"
                : "该文档未启用或未审核通过，已清除其向量（不会再被检索到）", data);
    }

    /**
     * 语义检索自测：输入一句游客的话，看知识库召回了哪些条目、各分项得分是多少。
     *
     * <p>分数拆开展示是这个接口的核心价值——排查"为什么这句话没召回"时，
     * 一眼就能区分是"语义没认出来"（vectorScore 低）还是"字面也没踩中"（keywordScore 低）。
     */
    @PostMapping("/vector/search")
    public Result<List<Map<String, Object>>> vectorSearch(@RequestBody Map<String, Object> body) {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index == null) {
            return Result.fail(VECTOR_DISABLED);
        }
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query")).trim();
        if (!StringUtils.hasText(query)) {
            return Result.fail("请输入要检索的问题");
        }
        int topK = parseInt(body.get("topK"), 5);
        double minScore = parseDouble(body.get("minScore"), 0);

        List<Map<String, Object>> list = new ArrayList<>();
        for (VectorHit hit : index.search(query, topK, minScore)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", hit.docId());
            item.put("title", hit.title());
            item.put("category", hit.category());
            item.put("percent", hit.percent());
            item.put("score", round4(hit.score()));
            item.put("vectorScore", round4(hit.vectorScore()));
            item.put("keywordScore", round4(hit.keywordScore()));
            item.put("titleHit", hit.titleHit());
            item.put("matchedType", hit.matchedType());
            item.put("matchedChunk", hit.matchedChunk());
            item.put("content", hit.content());
            list.add(item);
        }
        return Result.ok("命中 " + list.size() + " 条", list);
    }

    private static int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
