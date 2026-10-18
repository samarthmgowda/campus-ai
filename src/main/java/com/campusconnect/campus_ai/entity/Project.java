package com.campusconnect.campus_ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String domain;

    // comma-separated, same format as Student.skills
    private String requiredSkills;

    private String status; // ACTIVE or COMPLETED

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private Student owner;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ProjectMember> members = new ArrayList<>();

    public Project() {}

    public Project(String name, String description, String domain, String requiredSkills, String status, Student owner) {
        this.name = name;
        this.description = description;
        this.domain = domain;
        this.requiredSkills = requiredSkills;
        this.status = status;
        this.owner = owner;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDomain() { return domain; }
    public String getRequiredSkills() { return requiredSkills; }
    public String getStatus() { return status; }
    public Student getOwner() { return owner; }
    public List<ProjectMember> getMembers() { return members; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public void setStatus(String status) { this.status = status; }
    public void setOwner(Student owner) { this.owner = owner; }
}
