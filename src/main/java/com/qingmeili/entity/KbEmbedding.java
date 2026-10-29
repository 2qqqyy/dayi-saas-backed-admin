package com.qingmeili.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库向量索引表（RAG 语义检索）。
 *
 * <p>一条 kb_document 会被切成若干分片（标题片 + 正文片），每个分片一行。
 * {@code embedding} 是 float 数组的 JSON 序列化（text-embedding-v3 为 1024 维），
 * 之所以不存二进制 BLOB：一是便于在数据库工具里直接查看、答辩演示时能一眼看到"向量长什么样"，
 * 二是 6 位小数的精度对余弦相似度而言完全够用，体积也不大。
 *
 * <p><b>刻意不加 {@code deleted} 字段</b>：向量是索引而不是业务数据，
 * 文档被修改或删除时应当整段替换/直接消失。若带上逻辑删除字段，
 * MyBatis-Plus 的删除会退化成 UPDATE，历史向量被留在表里——虽然能靠 deleted=0 过滤掉，
 * 但索引表会持续膨胀。这里让删除保持物理删除，语义更干净。
 */
@Data
@TableName("kb_embedding")
public class KbEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库文档 id */
    private Long docId;

    /** 分片序号，同一文档内唯一 */
    private Integer chunkIndex;

    /** 分片类型：title / body */
    private String chunkType;

    /** 参与向量化的原文片段 */
    private String content;

    /** 向量的 JSON 序列化 */
    private String embedding;

    /** 向量维度 */
    private Integer dim;

    /** 生成该向量的嵌入模型名 */
    private String model;

    /** 生成该向量时的文档版本号，用于增量刷新判重 */
    private Integer docVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
