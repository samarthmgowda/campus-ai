package com.campusconnect.campus_ai.entity;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @ManyToOne
    @JoinColumn(name = "document_id")
    @JsonIgnore
    private Document document;

    private String content;

    private Integer pageNumber;

    private String section;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, Integer pageNumber, String section) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.section = section;
    }

    public Document getDocument() {
        return document;
    }

    @JsonIgnore
   public float[] getEmbedding() { return embedding; }

    public void setDocument(Document document) {
        this.document = document;
    }

    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public void setId(Long id) { this.id = id; }


    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getSection() {
        return section;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setSection(String section) {
        this.section = section;
    }
}
