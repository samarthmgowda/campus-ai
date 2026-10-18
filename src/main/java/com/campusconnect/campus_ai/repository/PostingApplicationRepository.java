package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.PostingApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostingApplicationRepository extends JpaRepository<PostingApplication, Long> {
    Optional<PostingApplication> findByPostingIdAndStudentId(Long postingId, Long studentId);
    List<PostingApplication> findByPostingIdOrderByCreatedAtDesc(Long postingId);
    List<PostingApplication> findByPostingIdAndStatusOrderByCreatedAtDesc(Long postingId, String status);
    List<PostingApplication> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<PostingApplication> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);
    long countByPostingIdAndStatus(Long postingId, String status);
}
