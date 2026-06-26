package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 文档分块（pkc_doc_chunk）：RAG 检索的基本单元。
 * 每个 chunk 对应文档中的一段连续内容（按章节/页面切割，方案 §8）。
 */
@Entity
@Table(name = "pkc_doc_chunk",
        indexes = @Index(columnList = "fileId, seq"))
public class DocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fileId;

    /** 在文件内的顺序（0-based） */
    @Column(nullable = false)
    private int seq;

    /** 章节路径，如 "3 部署架构 > 3.2 中间件"，可为空 */
    @Column(length = 512)
    private String headingPath;

    /** 分块正文（最大约 2000 汉字 / 4000 字节） */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 来源页码（PDF / Word 分页时有值，其余为 0） */
    private int pageNo;

    /** 估算 token 数（字符数 / 1.5 的粗略值） */
    private int tokenCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getHeadingPath() {
        return headingPath;
    }

    public void setHeadingPath(String headingPath) {
        this.headingPath = headingPath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }
}
