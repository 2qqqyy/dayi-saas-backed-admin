package com.qingmeili.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingmeili.ai.embedding.KbVectorIndex;
import com.qingmeili.common.exception.BusinessException;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.KbDocument;
import com.qingmeili.mapper.KbDocumentMapper;
import com.qingmeili.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 知识库文档服务实现
 *
 * <p>增删改都会联动向量索引：写库成功后异步重建该文档的向量，
 * 做到"后台改完，下一次提问就用新内容"，不需要手工重建、也不需要重启服务。
 * 向量化是异步且不抛异常的，所以嵌入服务故障不会影响后台保存文档本身。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentService {

    private final ObjectProvider<KbVectorIndex> vectorIndexProvider;

    @Override
    public Result<PageResult<KbDocument>> page(Integer page, Integer pageSize) {
        Page<KbDocument> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        Page<KbDocument> result = baseMapper.selectPage(pageParam,
                new LambdaQueryWrapper<KbDocument>().orderByDesc(KbDocument::getCreateTime));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, pageSize));
    }

    @Override
    public Result<KbDocument> create(KbDocument document) {
        document.setVersion(1);
        document.setAuditStatus(0);
        document.setStatus(1);
        baseMapper.insert(document);
        reindex(document.getId());
        return Result.ok("创建成功", document);
    }

    @Override
    public Result<KbDocument> update(Long id, KbDocument document) {
        KbDocument existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("文档不存在");
        }
        document.setId(id);
        document.setVersion(existing.getVersion() + 1);
        baseMapper.updateById(document);
        reindex(id);
        return Result.ok("修改成功", baseMapper.selectById(id));
    }

    @Override
    public Result<Void> delete(Long id) {
        KbDocument existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("文档不存在");
        }
        baseMapper.deleteById(id);
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index != null) {
            index.removeDocument(id);
        }
        return Result.ok("删除成功", null);
    }

    /** 异步重建该文档的向量；向量索引未启用时静默跳过 */
    private void reindex(Long id) {
        KbVectorIndex index = vectorIndexProvider.getIfAvailable();
        if (index != null) {
            index.indexDocumentAsync(id);
        }
    }
}
