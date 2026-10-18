package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The student being reviewed
    @ManyToOne
    @JoinColumn(name = "reviewee_id")
    private Student reviewee;

    // The reviewer (student or alumni)
    @ManyToOne
    @JoinColumn(name = "reviewer_id")
    private Student reviewer;

    // 1–5 stars
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    // Context: which project/collaboration this review is for (optional)
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Review() {}

    public Review(Student reviewee, Student reviewer, Integer rating, String comment, Project project) {
        this.reviewee = reviewee;
        this.reviewer = reviewer;
        this.rating = rating;
        this.comment = comment;
        this.project = project;
    }

    public Long getId() { return id; }
    public Student getReviewee() { return reviewee; }
    public Student getReviewer() { return reviewer; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Project getProject() { return project; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setReviewee(Student reviewee) { this.reviewee = reviewee; }
    public void setReviewer(Student reviewer) { this.reviewer = reviewer; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setComment(String comment) { this.comment = comment; }
    public void setProject(Project project) { this.project = project; }
}