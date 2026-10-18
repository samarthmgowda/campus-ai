package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_endorsement", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"endorser_id", "endorsee_id", "skill"})
})
public class SkillEndorsement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "endorser_id")
    private Student endorser;

    @ManyToOne
    @JoinColumn(name = "endorsee_id")
    private Student endorsee;

    // canonical skill name (e.g. "Java", "React")
    private String skill;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SkillEndorsement() {}

    public SkillEndorsement(Student endorser, Student endorsee, String skill) {
        this.endorser = endorser;
        this.endorsee = endorsee;
        this.skill = skill;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getEndorser() { return endorser; }
    public Student getEndorsee() { return endorsee; }
    public String getSkill() { return skill; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setEndorser(Student endorser) { this.endorser = endorser; }
    public void setEndorsee(Student endorsee) { this.endorsee = endorsee; }
    public void setSkill(String skill) { this.skill = skill; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
