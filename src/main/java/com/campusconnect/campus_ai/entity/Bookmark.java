package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookmark", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "refType", "refId"})
})
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    // POST, EVENT, PROJECT
    private String refType;

    private Long refId;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Bookmark() {}

    public Bookmark(Student student, String refType, Long refId) {
        this.student = student;
        this.refType = refType;
        this.refId = refId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStudent(Student student) { this.student = student; }
    public void setRefType(String refType) { this.refType = refType; }
    public void setRefId(Long refId) { this.refId = refId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
