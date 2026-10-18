package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.TeammateRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeammateRequestRepository extends JpaRepository<TeammateRequest, Long> {

    /** All requests sent to a candidate, newest first. */
    List<TeammateRequest> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    /** Requests sent to a candidate filtered by status, newest first. */
    List<TeammateRequest> findByCandidateIdAndStatusOrderByCreatedAtDesc(Long candidateId, String status);

    /** All requests for a project, newest first. */
    List<TeammateRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /** Requests for a project filtered by status, newest first. */
    List<TeammateRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, String status);

    /** Check for existing request between project and candidate. */
    Optional<TeammateRequest> findByProjectIdAndCandidateId(Long projectId, Long candidateId);

    /** Requests sent by a specific requester, newest first. */
    List<TeammateRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
}
