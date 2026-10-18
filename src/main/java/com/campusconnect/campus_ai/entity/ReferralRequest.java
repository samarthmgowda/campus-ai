package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_request", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "alumni_id"})
})
public class ReferralRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne
    @JoinColumn(name = "alumni_id")
    private Alumni alumni;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String company;
    private String role;

    private String status = "PENDING";

    private LocalDateTime createdAt = LocalDateTime.now();

    public ReferralRequest() {}

    public ReferralRequest(Student student, Alumni alumni, String message, String company, String role) {
        this.student = student;
        this.alumni = alumni;
        this.message = message != null ? message.trim() : null;
        this.company = company != null ? company.trim() : null;
        this.role = role != null ? role.trim() : null;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Alumni getAlumni() { return alumni; }
    public String getMessage() { return message; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStudent(Student student) { this.student = student; }
    public void setAlumni(Alumni alumni) { this.alumni = alumni; }
    public void setMessage(String message) { this.message = message; }
    public void setCompany(String company) { this.company = company; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
