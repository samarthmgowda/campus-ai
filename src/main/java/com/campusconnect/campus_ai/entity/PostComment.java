package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_comment")
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private CommunityPost post;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Student author;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hidden = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public PostComment() {}
    public PostComment(CommunityPost post, Student author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; }
    public Student getAuthor() { return author; }
    public String getContent() { return content; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setPost(CommunityPost post) { this.post = post; }
    public void setAuthor(Student author) { this.author = author; }
    public void setContent(String content) { this.content = content; }
}