package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ReferralRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralRequestRepository extends JpaRepository<ReferralRequest, Long> {

    Optional<ReferralRequest> findByStudentIdAndAlumniId(Long studentId, Long alumniId);

    List<ReferralRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<ReferralRequest> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, String status);

    List<ReferralRequest> findByAlumniIdOrderByCreatedAtDesc(Long alumniId);

    List<ReferralRequest> findByAlumniIdAndStatusOrderByCreatedAtDesc(Long alumniId, String status);
}
