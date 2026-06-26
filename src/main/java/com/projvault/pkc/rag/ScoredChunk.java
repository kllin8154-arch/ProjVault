package com.projvault.pkc.rag;

import com.projvault.pkc.file.DocChunk;

/**
 * 带检索得分的 DocChunk 包装（检索层内部使用）。
 *
 * @param chunk 文档分块
 * @param score 归一化相关度（0~1）
 */
public record ScoredChunk(DocChunk chunk, double score) {
}
