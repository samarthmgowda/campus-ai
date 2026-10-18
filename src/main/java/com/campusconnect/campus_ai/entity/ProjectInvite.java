package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_invite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "invitee_id"})
})
public class ProjectInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name = "inviter_id")
    private Student inviter;

    @ManyToOne
    @JoinColumn(name = "invitee_id")
    private Student invitee;

    // PENDING, ACCEPTED, REJECTED
    private String status = "PENDING";

    private LocalDateTime createdAt = LocalDateTime.now();

    public ProjectInvite() {}

    public ProjectInvite(Project project, Student inviter, Student invitee) {
        this.project = project;
        this.inviter = inviter;
        this.invitee = invitee;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Student getInviter() { return inviter; }
    public Student getInvitee() { return invitee; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setProject(Project project) { this.project = project; }
    public void setInviter(Student inviter) { this.inviter = inviter; }
    public void setInvitee(Student invitee) { this.invitee = invitee; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
