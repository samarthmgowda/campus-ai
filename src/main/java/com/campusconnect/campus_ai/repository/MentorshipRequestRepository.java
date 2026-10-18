package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.MentorshipRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorshipRequestRepository extends JpaRepository<MentorshipRequest, Long> {

    Optional<MentorshipRequest> findByStudentIdAndAlumniId(Long studentId, Long alumniId);

    List<MentorshipRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<MentorshipRequest> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);

    List<MentorshipRequest> findByAlumniIdOrderByCreatedAtDesc(Long alumniId);

    List<MentorshipRequest> findByAlumniIdAndStatusOrderByCreatedAtDesc(Long alumniId, String status);
}
