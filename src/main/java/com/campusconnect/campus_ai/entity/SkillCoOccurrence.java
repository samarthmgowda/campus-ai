package com.campusconnect.campus_ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class SkillCoOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "skill_a_id")
    @JsonIgnore
    private Skill skillA;

    @ManyToOne
    @JoinColumn(name = "skill_b_id")
    @JsonIgnore
    private Skill skillB;

    private Integer count; // how many profiles have both skills

    public SkillCoOccurrence() {}

    public SkillCoOccurrence(Skill skillA, Skill skillB, int count) {
        this.skillA = skillA;
        this.skillB = skillB;
        this.count = count;
    }

    public Long getId() { return id; }
    public Skill getSkillA() { return skillA; }
    public Skill getSkillB() { return skillB; }
    public Integer getCount() { return count; }

    public void setSkillA(Skill skillA) { this.skillA = skillA; }
    public void setSkillB(Skill skillB) { this.skillB = skillB; }
    public void setCount(Integer count) { this.count = count; }
}