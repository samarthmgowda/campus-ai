package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ModerationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationReportRepository extends JpaRepository<ModerationReport, Long> {

    List<ModerationReport> findByStatusOrderByCreatedAtDesc(String status);

    List<ModerationReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    List<ModerationReport> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    long countByStatus(String status);
}
