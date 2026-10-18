package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    long countByPostId(Long postId);
    Optional<PostLike> findByPostIdAndStudentId(Long postId, Long studentId);
    List<PostLike> findByPostId(Long postId);
}