package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByStudentIdAndRefTypeAndRefId(Long studentId, String refType, Long refId);

    boolean existsByStudentIdAndRefTypeAndRefId(Long studentId, String refType, Long refId);

    List<Bookmark> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Bookmark> findByStudentIdAndRefTypeOrderByCreatedAtDesc(Long studentId, String refType);

    long countByStudentId(Long studentId);
}
