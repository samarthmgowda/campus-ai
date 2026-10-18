package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Alumni {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String branch;
    private Integer graduationYear;

    // comma-separated, e.g. "Java, Python, System Design"
    private String skills;

    @Column(name = "current_role_name")
    private String currentRole;
    @Column(name = "current_company_name")
    private String currentCompany;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String linkedinUrl;

    // e.g. "MENTORING", "REFERRALS", "BOTH", "UNAVAILABLE"
    private String availability;

    @OneToMany(mappedBy = "alumni", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Roadmap> roadmaps = new ArrayList<>();

    public Alumni() {}

    public Alumni(String name, String branch, Integer graduationYear, String skills,
                  String currentRole, String currentCompany, String bio,
                  String linkedinUrl, String availability) {
        this.name = name;
        this.branch = branch;
        this.graduationYear = graduationYear;
        this.skills = skills;
        this.currentRole = currentRole;
        this.currentCompany = currentCompany;
        this.bio = bio;
        this.linkedinUrl = linkedinUrl;
        this.availability = availability;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBranch() { return branch; }
    public Integer getGraduationYear() { return graduationYear; }
    public String getSkills() { return skills; }
    public String getCurrentRole() { return currentRole; }
    public String getCurrentCompany() { return currentCompany; }
    public String getBio() { return bio; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getAvailability() { return availability; }
    public List<Roadmap> getRoadmaps() { return roadmaps; }

    public void setName(String name) { this.name = name; }
    public void setBranch(String branch) { this.branch = branch; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
    public void setSkills(String skills) { this.skills = skills; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }
    public void setCurrentCompany(String currentCompany) { this.currentCompany = currentCompany; }
    public void setBio(String bio) { this.bio = bio; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public void setAvailability(String availability) { this.availability = availability; }
}