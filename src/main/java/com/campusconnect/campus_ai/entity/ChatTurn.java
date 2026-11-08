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

    /**
     * Conversation (thread) this turn belongs to. Turns from the same chat share
     * one id so the UI can list separate conversations and scope memory to the
     * active one. Legacy rows predate threads and carry null — read as the
     * default conversation.
     */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private boolean answered;

    /** JSON-encoded List<ToolResult.EntityCard> rendered as cards under the answer. */
    @Column(columnDefinition = "TEXT")
    private String cards;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatTurn() {}

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getConversationId() { return conversationId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isAnswered() { return answered; }
    public String getCards() { return cards; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStudent(Student student) { this.student = student; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public void setQuestion(String question) { this.question = question; }
    public void setAnswer(String answer) { this.answer = answer; }
    public void setAnswered(boolean answered) { this.answered = answered; }
    public void setCards(String cards) { this.cards = cards; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
