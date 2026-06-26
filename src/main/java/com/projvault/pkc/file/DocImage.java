package com.projvault.pkc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * 文档内嵌图片（pkc_doc_image）：从 docx/xlsx/pptx 的 media 目录提取，字节存库。
 */
@Entity
@Table(name = "pkc_doc_image", indexes = @Index(columnList = "fileId"))
public class DocImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    private int seq;

    @Column(length = 256)
    private String mediaName;

    @Column(length = 16)
    private String ext;

    private long size;

    @Lob
    @Column(name = "img_data")
    private byte[] data;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getMediaName() { return mediaName; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }

    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
}
