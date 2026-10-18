package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posting_application", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"posting_id", "student_id"})
})
public class PostingApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "posting_id")
    private RecruiterPosting posting;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private String status = "APPLIED"; // APPLIED | SHORTLISTED | REJECTED | WITHDRAWN not used as state

    private LocalDateTime createdAt = LocalDateTime.now();

    public PostingApplication() {}

    public PostingApplication(RecruiterPosting posting, Student student) {
        this.posting = posting;
        this.student = student;
        this.status = "APPLIED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public RecruiterPosting getPosting() { return posting; }
    public Student getStudent() { return student; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setPosting(RecruiterPosting posting) { this.posting = posting; }
    public void setStudent(Student student) { this.student = student; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
