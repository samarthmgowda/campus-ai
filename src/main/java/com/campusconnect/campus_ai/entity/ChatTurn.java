package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_turn")
public class ChatTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private boolean answered;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatTurn() {}

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isAnswered() { return answered; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStudent(Student student) { this.student = student; }
    public void setQuestion(String question) { this.question = question; }
    public void setAnswer(String answer) { this.answer = answer; }
    public void setAnswered(boolean answered) { this.answered = answered; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
