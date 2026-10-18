package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_attendance", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "student_id"})
})
public class EventAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    private LocalDateTime joinedAt = LocalDateTime.now();

    public EventAttendance() {}

    public EventAttendance(Event event, Student student) {
        this.event = event;
        this.student = student;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public Student getStudent() { return student; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    public void setEvent(Event event) { this.event = event; }
    public void setStudent(Student student) { this.student = student; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
