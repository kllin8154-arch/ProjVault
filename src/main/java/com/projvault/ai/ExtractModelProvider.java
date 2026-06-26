package com.projvault.ai;

import java.util.List;

/**
 * 配置项/服务器资产抽取模型接口。
 * 输入为「正则粗筛命中的候选 + 上下文段落」（两级漏斗的第二级，方案 §9）。
 */
public interface ExtractModelProvider {

    /**
     * 从文档片段中抽取结构化配置项。
     *
     * @param docName 来源文档名
     * @param context 候选项所在的上下文段落
     * @return 结构化配置项列表，无命中返回空列表
     */
    List<ExtractedItem> extract(String docName, String context);
}
