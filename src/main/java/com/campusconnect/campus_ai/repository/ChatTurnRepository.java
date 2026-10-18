package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    List<ChatTurn> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    long countByStudentId(Long studentId);

    @Modifying
    @Transactional
    void deleteByStudentId(Long studentId);
}
