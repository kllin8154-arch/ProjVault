package com.projvault.ai;

/**
 * 知识图谱节点/边生成模型接口。
 * 输出遵循 UA 兼容 Schema（方案 §11，version 1.1-pkc）：
 * {"nodes":[{id,type,name,summary,tags,...}],"edges":[{source,target,type,weight,...}]}
 * 由调用方（图谱合并器）负责别名归一、引用完整性校验与 spurious 边剔除。
 */
public interface GraphModelProvider {

    /**
     * 对一个语义批次的文档内容生成图谱片段。
     *
     * @param batchContent 批次内容（多个文档的摘要+结构信息拼接）
     * @param language     节点摘要输出语言，如 "zh"
     * @return 图谱片段 JSON 字符串
     */
    String generateGraphFragment(String batchContent, String language);
}
