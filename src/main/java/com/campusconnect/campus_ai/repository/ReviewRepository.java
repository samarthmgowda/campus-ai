package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByRevieweeId(Long revieweeId);
    List<Review> findByReviewerId(Long reviewerId);
    List<Review> findByProjectId(Long projectId);
    List<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);
}