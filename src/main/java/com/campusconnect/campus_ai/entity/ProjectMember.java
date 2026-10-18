package com.campusconnect.campus_ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "project_member")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    @JsonIgnore
    private Project project;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private String role;

    public ProjectMember() {}

    public ProjectMember(Project project, Student student, String role) {
        this.project = project;
        this.student = student;
        this.role = role;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Student getStudent() { return student; }
    public String getRole() { return role; }

    public void setProject(Project project) { this.project = project; }
    public void setStudent(Student student) { this.student = student; }
    public void setRole(String role) { this.role = role; }
}
