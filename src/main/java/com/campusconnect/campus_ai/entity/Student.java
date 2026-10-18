package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;

@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String branch;
    private Integer year; // 1..4

    // comma-separated, e.g. "Java, Python, React"
    private String skills;
    private String interests;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String githubUrl;
    private String linkedinUrl;

    // e.g. "AVAILABLE", "BUSY"
    private String availability;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean suspended = false;

    public Student() {}

    public Student(String name, String branch, Integer year, String skills,
                   String interests, String bio, String githubUrl,
                   String linkedinUrl, String availability) {
        this.name = name;
        this.branch = branch;
        this.year = year;
        this.skills = skills;
        this.interests = interests;
        this.bio = bio;
        this.githubUrl = githubUrl;
        this.linkedinUrl = linkedinUrl;
        this.availability = availability;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBranch() { return branch; }
    public Integer getYear() { return year; }
    public String getSkills() { return skills; }
    public String getInterests() { return interests; }
    public String getBio() { return bio; }
    public String getGithubUrl() { return githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getAvailability() { return availability; }
    public boolean isSuspended() { return suspended; }

    public void setName(String name) { this.name = name; }
    public void setBranch(String branch) { this.branch = branch; }
    public void setYear(Integer year) { this.year = year; }
    public void setSkills(String skills) { this.skills = skills; }
    public void setInterests(String interests) { this.interests = interests; }
    public void setBio(String bio) { this.bio = bio; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public void setAvailability(String availability) { this.availability = availability; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
}
