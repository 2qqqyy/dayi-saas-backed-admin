package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.PageResult;
import com.qingmeili.common.result.Result;
import com.qingmeili.entity.KbDocument;

/**
 * 知识库文档服务
 */
public interface KbDocumentService extends IService<KbDocument> {

    Result<PageResult<KbDocument>> page(Integer page, Integer pageSize);

    Result<KbDocument> create(KbDocument document);

    Result<KbDocument> update(Long id, KbDocument document);

    /**
     * 删除文档，并同步清掉它的向量，避免已删除的文档继续被检索到。
     */
    Result<Void> delete(Long id);
}
