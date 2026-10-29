package com.qingmeili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingmeili.entity.KbEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库向量索引 Mapper
 */
@Mapper
public interface KbEmbeddingMapper extends BaseMapper<KbEmbedding> {
}
