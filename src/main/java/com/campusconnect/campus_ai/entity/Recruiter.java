package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;

@Entity
public class Recruiter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String company;
    private String role; // e.g. "Tech Recruiter", "Hiring Manager"

    private String email;
    private String linkedinUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    public Recruiter() {}

    public Recruiter(String name, String company, String role, String email, String linkedinUrl, String bio) {
        this.name = name;
        this.company = company;
        this.role = role;
        this.email = email;
        this.linkedinUrl = linkedinUrl;
        this.bio = bio;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public String getEmail() { return email; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getBio() { return bio; }

    public void setName(String name) { this.name = name; }
    public void setCompany(String company) { this.company = company; }
    public void setRole(String role) { this.role = role; }
    public void setEmail(String email) { this.email = email; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public void setBio(String bio) { this.bio = bio; }
}
