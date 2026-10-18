package com.campusconnect.campus_ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class SkillAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String alias; // e.g. "JS", "React.js", "node"

    @ManyToOne
    @JoinColumn(name = "skill_id")
    @JsonIgnore
    private Skill skill;

    public SkillAlias() {}

    public SkillAlias(String alias, Skill skill) {
        this.alias = alias;
        this.skill = skill;
    }

    public Long getId() { return id; }
    public String getAlias() { return alias; }
    public Skill getSkill() { return skill; }

    public void setAlias(String alias) { this.alias = alias; }
    public void setSkill(Skill skill) { this.skill = skill; }
}