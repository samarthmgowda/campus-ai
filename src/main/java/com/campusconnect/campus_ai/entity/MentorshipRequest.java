package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mentorship_request", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "alumni_id"})
})
public class MentorshipRequest {

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

    // PENDING, ACCEPTED, REJECTED, WITHDRAWN not used as row state (deleted on withdraw)
    private String status = "PENDING";

    private LocalDateTime createdAt = LocalDateTime.now();

    public MentorshipRequest() {}

    public MentorshipRequest(Student student, Alumni alumni, String message) {
        this.student = student;
        this.alumni = alumni;
        this.message = message != null ? message.trim() : null;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Alumni getAlumni() { return alumni; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStudent(Student student) { this.student = student; }
    public void setAlumni(Alumni alumni) { this.alumni = alumni; }
    public void setMessage(String message) { this.message = message; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
