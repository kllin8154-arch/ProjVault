package com.projvault.ai;

/**
 * 文本向量化接口。实现可为内网 embedding 服务（bge / m3e / Qwen-embedding 等）。
 */
public interface EmbeddingProvider {

    /**
     * @return 归一化后的向量
     */
    float[] embed(String text);

    /** 向量维度（建索引时使用） */
    int dimension();
}
