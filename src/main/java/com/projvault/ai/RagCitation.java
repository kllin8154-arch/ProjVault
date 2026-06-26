package com.projvault.ai;

/**
 * AI 回答的引用来源（chunk 级溯源，方案 §10：无据回答必须声明未找到）。
 *
 * @param fileName    来源文档名
 * @param headingPath 章节路径，如 "3 部署架构 > 3.2 中间件"
 * @param chunkId     来源 chunk 主键，前端用于跳转原文
 * @param score       检索相关度
 */
public record RagCitation(String fileName, String headingPath, Long chunkId, double score) {
}
