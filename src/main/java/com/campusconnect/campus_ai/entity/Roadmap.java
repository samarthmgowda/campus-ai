package com.campusconnect.campus_ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class Roadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // e.g. "Backend Developer Roadmap"
    private String targetRole; // e.g. "Backend Engineer"

    @Column(columnDefinition = "TEXT")
    private String description; // step-by-step guide

    /**
     * Learning resources attached to the roadmap, stored as a JSON array of
     * {"type": "youtube"|"blog"|"pdf"|"website", "url": "..."} objects.
     *
     * Kept as raw JSON text rather than a child table: resources are only ever
     * read and written as a whole list alongside the roadmap, never queried by
     * type or URL, so a join table would buy nothing but another migration.
     * Null means "no resources", which is the case for every roadmap seeded
     * before this column existed.
     */
    @Column(columnDefinition = "TEXT")
    private String resources;

    @ManyToOne
    @JoinColumn(name = "alumni_id")
    @JsonIgnore
    private Alumni alumni;

    public Roadmap() {}

    public Roadmap(String title, String targetRole, String description, Alumni alumni) {
        this.title = title;
        this.targetRole = targetRole;
        this.description = description;
        this.alumni = alumni;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getTargetRole() { return targetRole; }
    public String getDescription() { return description; }
    public String getResources() { return resources; }
    public Alumni getAlumni() { return alumni; }

    public void setTitle(String title) { this.title = title; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public void setDescription(String description) { this.description = description; }
    public void setResources(String resources) { this.resources = resources; }
    public void setAlumni(Alumni alumni) { this.alumni = alumni; }
}