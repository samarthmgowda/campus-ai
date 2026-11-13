package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission, Long> {

    List<ProjectSubmission> findByGitHubRepoUrl(String githubRepoUrl);

    List<ProjectSubmission> findByStatusOrderByCreatedAtDesc(String status);

    List<ProjectSubmission> findPendingByUserId(String userId);
}