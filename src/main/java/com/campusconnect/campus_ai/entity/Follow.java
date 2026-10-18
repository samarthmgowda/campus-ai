package com.campusconnect.campus_ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "follow", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"follower_id", "following_id"})
})
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "follower_id")
    private Student follower;

    @ManyToOne
    @JoinColumn(name = "following_id")
    private Student following;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Follow() {}

    public Follow(Student follower, Student following) {
        this.follower = follower;
        this.following = following;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getFollower() { return follower; }
    public Student getFollowing() { return following; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setFollower(Student follower) { this.follower = follower; }
    public void setFollowing(Student following) { this.following = following; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
