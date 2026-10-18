package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ProjectInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInviteRepository extends JpaRepository<ProjectInvite, Long> {

    Optional<ProjectInvite> findByProjectIdAndInviteeId(Long projectId, Long inviteeId);

    List<ProjectInvite> findByInviteeIdOrderByCreatedAtDesc(Long inviteeId);

    List<ProjectInvite> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId, String status);

    List<ProjectInvite> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);

    List<ProjectInvite> findByInviterIdOrderByCreatedAtDesc(Long inviterId);

    boolean existsByProjectIdAndInviteeIdAndStatus(Long projectId, Long inviteeId, String status);
}
