package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private Student recipient;

    // LIKE, COMMENT, MESSAGE, REVIEW
    private String type;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    // e.g. POST, MESSAGE, REVIEW
    private String refType;
    private Long refId;

    private boolean readFlag = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(Student recipient, String type, String title, String body, String refType, Long refId) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public boolean isReadFlag() { return readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setRecipient(Student recipient) { this.recipient = recipient; }
    public void setType(String type) { this.type = type; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setRefType(String refType) { this.refType = refType; }
    public void setRefId(Long refId) { this.refId = refId; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
