package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String type; // HACKATHON, WORKSHOP

    private LocalDate eventDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String location;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;

    public Event() {}

    public Event(String title, String type, LocalDate eventDate, String description, String location) {
        this.title = title;
        this.type = type;
        this.eventDate = eventDate;
        this.description = description;
        this.location = location;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public LocalDate getEventDate() { return eventDate; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public boolean isHidden() { return hidden; }

    public void setTitle(String title) { this.title = title; }
    public void setType(String type) { this.type = type; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public void setDescription(String description) { this.description = description; }
    public void setLocation(String location) { this.location = location; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
