package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer maxMembers;

    private Integer currentMembers;

    private String status;

    private LocalDateTime formedAt;

    private Long eventId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Team() {}

    public Team(String name, String description, Integer maxMembers, Integer currentMembers,
                String status, LocalDateTime formedAt, Long eventId,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.name = name;
        this.description = description;
        this.maxMembers = maxMembers;
        this.currentMembers = currentMembers;
        this.status = status;
        this.formedAt = formedAt;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getMaxMembers() { return maxMembers; }
    public Integer getCurrentMembers() { return currentMembers; }
    public String getStatus() { return status; }
    public LocalDateTime getFormedAt() { return formedAt; }
    public Long getEventId() { return eventId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public void setCurrentMembers(Integer currentMembers) { this.currentMembers = currentMembers; }
    public void setStatus(String status) { this.status = status; }
    public void setFormedAt(LocalDateTime formedAt) { this.formedAt = formedAt; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}