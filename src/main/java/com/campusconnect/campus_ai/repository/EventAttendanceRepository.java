package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.EventAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, Long> {

    Optional<EventAttendance> findByEventIdAndStudentId(Long eventId, Long studentId);

    List<EventAttendance> findByEventIdOrderByJoinedAtAsc(Long eventId);

    List<EventAttendance> findByStudentIdOrderByJoinedAtDesc(Long studentId);

    long countByEventId(Long eventId);

    boolean existsByEventIdAndStudentId(Long eventId, Long studentId);
}
