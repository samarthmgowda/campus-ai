package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_submissions")
public class ProjectSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String githubRepoUrl;

    private String githubOwner;

    private String branchName;

    private String status;

    private Integer progressPercentage;

    private LocalDateTime clonedAt;

    private LocalDateTime lastBuildAt;

    private Double score;

    private String feedback;

    private String userId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public ProjectSubmission() {}

    public ProjectSubmission(String title, String description, String githubRepoUrl,
                             String githubOwner, String branchName, String status,
                             Integer progressPercentage, LocalDateTime clonedAt,
                             LocalDateTime lastBuildAt, Double score, String feedback,
                             String userId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.title = title;
        this.description = description;
        this.githubRepoUrl = githubRepoUrl;
        this.githubOwner = githubOwner;
        this.branchName = branchName;
        this.status = status;
        this.progressPercentage = progressPercentage;
        this.clonedAt = clonedAt;
        this.lastBuildAt = lastBuildAt;
        this.score = score;
        this.feedback = feedback;
        this.userId = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getGithubRepoUrl() { return githubRepoUrl; }
    public String getGithubOwner() { return githubOwner; }
    public String getBranchName() { return branchName; }
    public String getStatus() { return status; }
    public Integer getProgressPercentage() { return progressPercentage; }
    public LocalDateTime getClonedAt() { return clonedAt; }
    public LocalDateTime getLastBuildAt() { return lastBuildAt; }
    public Double getScore() { return score; }
    public String getFeedback() { return feedback; }
    public String getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setGithubRepoUrl(String githubRepoUrl) { this.githubRepoUrl = githubRepoUrl; }
    public void setGithubOwner(String githubOwner) { this.githubOwner = githubOwner; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public void setStatus(String status) { this.status = status; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }
    public void setClonedAt(LocalDateTime clonedAt) { this.clonedAt = clonedAt; }
    public void setLastBuildAt(LocalDateTime lastBuildAt) { this.lastBuildAt = lastBuildAt; }
    public void setScore(Double score) { this.score = score; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}