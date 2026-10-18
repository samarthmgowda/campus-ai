package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name; // canonical name, e.g. "JavaScript"

    private String category; // e.g. "Language", "Framework", "Cloud", "ML", "Design"

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillAlias> aliases = new ArrayList<>();

    @OneToMany(mappedBy = "skillA", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillCoOccurrence> coOccurrencesAsA = new ArrayList<>();

    @OneToMany(mappedBy = "skillB", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillCoOccurrence> coOccurrencesAsB = new ArrayList<>();

    public Skill() {}

    public Skill(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public List<SkillAlias> getAliases() { return aliases; }
    public List<SkillCoOccurrence> getCoOccurrencesAsA() { return coOccurrencesAsA; }
    public List<SkillCoOccurrence> getCoOccurrencesAsB() { return coOccurrencesAsB; }

    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
}