package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_templates")
public class EventTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String templateName;

    @Column(columnDefinition = "JSON")
    private String formConfig;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private Boolean usesPgvector;

    public EventTemplate() {}

    public EventTemplate(String templateName, String formConfig, Boolean isActive, LocalDateTime createdAt, Boolean usesPgvector) {
        this.templateName = templateName;
        this.formConfig = formConfig;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.usesPgvector = usesPgvector;
    }

    public Long getId() { return id; }
    public String getTemplateName() { return templateName; }
    public String getFormConfig() { return formConfig; }
    public Boolean getActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Boolean getUsesPgvector() { return usesPgvector; }

    public void setId(Long id) { this.id = id; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public void setFormConfig(String formConfig) { this.formConfig = formConfig; }
    public void setActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUsesPgvector(Boolean usesPgvector) { this.usesPgvector = usesPgvector; }
}