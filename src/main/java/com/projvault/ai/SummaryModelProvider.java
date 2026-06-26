package com.projvault.ai;

/**
 * 文档摘要模型接口（决策 D1：业务层不绑定具体模型）。
 * 实现可为：Mock / 内网 Qwen / DeepSeek / GLM / Claude API / Ollama / vLLM。
 */
public interface SummaryModelProvider {

    /**
     * 生成文档摘要。
     *
     * @param docName  文档名（含扩展名，提供类型线索）
     * @param content  解析后的文本内容（调用方负责截断到模型上下文限制内）
     * @param language 输出语言，如 "zh"
     * @return 一段式摘要文本
     */
    String summarize(String docName, String content, String language);
}
