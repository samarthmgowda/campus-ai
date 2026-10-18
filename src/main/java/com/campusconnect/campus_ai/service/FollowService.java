package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Follow;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.FollowRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public FollowService(FollowRepository followRepository,
                         StudentRepository studentRepository,
                         NotificationService notificationService) {
        this.followRepository = followRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    public Map<String, Object> follow(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) throw new IllegalArgumentException("followerId and followingId required");
        if (followerId.equals(followingId)) throw new IllegalArgumentException("Cannot follow yourself");
        Student follower = studentRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("Follower not found: " + followerId));
        Student following = studentRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + followingId));
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return Map.of("followerId", followerId, "followingId", followingId, "status", "already_following",
                    "followingCount", followRepository.countByFollowerId(followerId), "followerCount", followRepository.countByFollowingId(followingId));
        }
        followRepository.save(new Follow(follower, following));
        try {
            notificationService.notify(followingId, "FOLLOW", follower.getName() + " followed you", null, "STUDENT", followerId);
        } catch (Exception ignored) {}
        return Map.of("followerId", followerId, "followingId", followingId, "status", "followed",
                "followingCount", followRepository.countByFollowerId(followerId), "followerCount", followRepository.countByFollowingId(followingId));
    }

    public Map<String, Object> unfollow(Long followerId, Long followingId) {
        var existing = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new IllegalArgumentException("Not following: " + followingId));
        followRepository.delete(existing);
        return Map.of("followerId", followerId, "followingId", followingId, "status", "unfollowed",
                "followingCount", followRepository.countByFollowerId(followerId), "followerCount", followRepository.countByFollowingId(followingId));
    }

    public List<PeerDto> getFollowing(Long followerId) {
        studentRepository.findById(followerId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + followerId));
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(followerId).stream()
                .map(f -> toDto(f.getFollowing(), f.getCreatedAt() != null ? f.getCreatedAt().toString() : null))
                .collect(Collectors.toList());
    }

    public List<PeerDto> getFollowers(Long followingId) {
        studentRepository.findById(followingId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + followingId));
        return followRepository.findByFollowingIdOrderByCreatedAtDesc(followingId).stream()
                .map(f -> toDto(f.getFollower(), f.getCreatedAt() != null ? f.getCreatedAt().toString() : null))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats(Long studentId) {
        studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        return Map.of("studentId", studentId, "followingCount", followRepository.countByFollowerId(studentId), "followerCount", followRepository.countByFollowingId(studentId));
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    public record PeerDto(Long studentId, String name, String branch, Integer year, String skills, String availability, String followedAt) {}

    private PeerDto toDto(Student s, String followedAt) {
        return new PeerDto(s.getId(), s.getName(), s.getBranch(), s.getYear(), s.getSkills(), s.getAvailability(), followedAt);
    }
}
