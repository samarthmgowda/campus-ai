package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A request from a project member to a candidate student to join their project,
 * driven by the skill-gap analysis. Lifecycle: PENDING → ACCEPTED / REJECTED / WITHDRAWN.
 * On ACCEPTED the candidate is auto-added as a ProjectMember.
 */
@Entity
@Table(name = "teammate_request", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "candidate_id"})
})
public class TeammateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    /** The project member who sent the request. */
    @ManyToOne
    @JoinColumn(name = "requester_id")
    private Student requester;

    /** The candidate being asked to join. */
    @ManyToOne
    @JoinColumn(name = "candidate_id")
    private Student candidate;

    /** PENDING, ACCEPTED, REJECTED, WITHDRAWN */
    private String status = "PENDING";

    /** Optional message from the requester explaining why this person was chosen. */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** Comma-separated skills that matched the project's gaps. */
    private String matchedSkills;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime respondedAt;

    public TeammateRequest() {}

    public TeammateRequest(Project project, Student requester, Student candidate,
                           String message, String matchedSkills) {
        this.project = project;
        this.requester = requester;
        this.candidate = candidate;
        this.message = message;
        this.matchedSkills = matchedSkills;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Student getRequester() { return requester; }
    public Student getCandidate() { return candidate; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getMatchedSkills() { return matchedSkills; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }

    public void setProject(Project project) { this.project = project; }
    public void setRequester(Student requester) { this.requester = requester; }
    public void setCandidate(Student candidate) { this.candidate = candidate; }
    public void setStatus(String status) { this.status = status; }
    public void setMessage(String message) { this.message = message; }
    public void setMatchedSkills(String matchedSkills) { this.matchedSkills = matchedSkills; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
