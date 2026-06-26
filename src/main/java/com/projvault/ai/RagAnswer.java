package com.projvault.ai;

import java.util.List;

/**
 * RAG 问答结果。
 *
 * @param answer    回答正文
 * @param citations 引用来源列表（强制返回，可为空列表表示"资料中未找到"）
 * @param grounded  是否有据：false 时前端需明显提示"资料中未找到依据"
 */
public record RagAnswer(String answer, List<RagCitation> citations, boolean grounded) {
}
