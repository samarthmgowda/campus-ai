package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eval_cases")
public class EvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String caseText;

    @Column(columnDefinition = "TEXT")
    private String fact1;

    @Column(columnDefinition = "TEXT")
    private String fact2;

    @Column(columnDefinition = "TEXT")
    private String fact3;

    private String expectedAnswerCategory;

    private LocalDateTime createdAt;

    public EvalCase() {}

    public EvalCase(String caseText, String fact1, String fact2, String fact3,
                    String expectedAnswerCategory, LocalDateTime createdAt) {
        this.caseText = caseText;
        this.fact1 = fact1;
        this.fact2 = fact2;
        this.fact3 = fact3;
        this.expectedAnswerCategory = expectedAnswerCategory;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCaseText() { return caseText; }
    public String getFact1() { return fact1; }
    public String getFact2() { return fact2; }
    public String getFact3() { return fact3; }
    public String getExpectedAnswerCategory() { return expectedAnswerCategory; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setCaseText(String caseText) { this.caseText = caseText; }
    public void setFact1(String fact1) { this.fact1 = fact1; }
    public void setFact2(String fact2) { this.fact2 = fact2; }
    public void setFact3(String fact3) { this.fact3 = fact3; }
    public void setExpectedAnswerCategory(String expectedAnswerCategory) { this.expectedAnswerCategory = expectedAnswerCategory; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}