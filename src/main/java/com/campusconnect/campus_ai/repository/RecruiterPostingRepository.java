package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.RecruiterPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruiterPostingRepository extends JpaRepository<RecruiterPosting, Long> {
    List<RecruiterPosting> findByStatusOrderByCreatedAtDesc(String status);
    List<RecruiterPosting> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    List<RecruiterPosting> findAllByOrderByCreatedAtDesc();
}
