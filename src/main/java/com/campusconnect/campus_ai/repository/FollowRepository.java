package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    List<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId);

    List<Follow> findByFollowingIdOrderByCreatedAtDesc(Long followingId);

    long countByFollowerId(Long followerId);

    long countByFollowingId(Long followingId);
}
