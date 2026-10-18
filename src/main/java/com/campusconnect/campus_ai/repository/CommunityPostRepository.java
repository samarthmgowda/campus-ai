package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    List<CommunityPost> findAllByOrderByCreatedAtDesc();
    List<CommunityPost> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}