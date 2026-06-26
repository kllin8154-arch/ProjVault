package com.projvault.ai;

import java.util.List;

/**
 * 项目问答模型接口。
 * 注意：上下文 chunk 必须在调用前按提问者权限过滤（决策 D3：检索前过滤，非生成后遮蔽）。
 */
public interface RagAnswerModelProvider {

    /**
     * 基于检索上下文回答问题。
     *
     * @param question      用户问题
     * @param contextChunks 已按权限过滤、已重排的上下文片段（含来源标注）
     * @return 带引用溯源的回答
     */
    RagAnswer answer(String question, List<String> contextChunks);
}
