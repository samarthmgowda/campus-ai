package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "post_like", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"post_id", "student_id"})
})
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private CommunityPost post;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    public PostLike() {}
    public PostLike(CommunityPost post, Student student) {
        this.post = post;
        this.student = student;
    }

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; }
    public Student getStudent() { return student; }

    public void setPost(CommunityPost post) { this.post = post; }
    public void setStudent(Student student) { this.student = student; }
}
