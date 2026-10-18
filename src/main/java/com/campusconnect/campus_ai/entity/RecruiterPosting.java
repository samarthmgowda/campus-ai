package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recruiter_posting")
public class RecruiterPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recruiter_id")
    private Recruiter recruiter;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String requiredSkills; // CSV normalized

    private String status = "OPEN"; // OPEN | CLOSED

    private LocalDateTime createdAt = LocalDateTime.now();

    public RecruiterPosting() {}

    public RecruiterPosting(Recruiter recruiter, String title, String description, String requiredSkills) {
        this.recruiter = recruiter;
        this.title = title;
        this.description = description;
        this.requiredSkills = requiredSkills;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Recruiter getRecruiter() { return recruiter; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRequiredSkills() { return requiredSkills; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setRecruiter(Recruiter recruiter) { this.recruiter = recruiter; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
