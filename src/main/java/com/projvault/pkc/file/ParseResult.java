package com.projvault.pkc.file;

import java.util.List;

/**
 * 单文件解析结果（内存传递）。
 *
 * @param fileId    文件 ID
 * @param fullText  全量提取文本（供 P4 摘要/语义分析使用）
 * @param chunks    分块列表（已落库，此处保留引用供 P4 使用）
 * @param parseOk   是否解析成功
 * @param errorMsg  失败时的错误摘要
 */
public record ParseResult(
        Long fileId,
        String fullText,
        List<DocChunk> chunks,
        boolean parseOk,
        String errorMsg) {

    public static ParseResult ok(Long fileId, String fullText, List<DocChunk> chunks) {
        return new ParseResult(fileId, fullText, chunks, true, null);
    }

    public static ParseResult fail(Long fileId, String errorMsg) {
        return new ParseResult(fileId, null, List.of(), false, errorMsg);
    }
}
