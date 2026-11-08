package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.ChatTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatTurnRepository extends JpaRepository<ChatTurn, Long> {

    List<ChatTurn> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<ChatTurn> findByStudentIdAndConversationIdOrderByCreatedAtDesc(Long studentId, String conversationId);

    long countByStudentId(Long studentId);

    @Modifying
    @Transactional
    void deleteByStudentId(Long studentId);

    @Modifying
    @Transactional
    void deleteByStudentIdAndConversationId(Long studentId, String conversationId);

    /**
     * Distinct conversations for a student with the newest turn time, newest first.
     * Returns rows of [conversationId, lastCreatedAt]. Legacy rows with a null
     * conversation id are coalesced to the '' default so they surface as one thread.
     */
    @Query("SELECT COALESCE(t.conversationId, ''), MAX(t.createdAt) " +
            "FROM ChatTurn t WHERE t.student.id = :studentId " +
            "GROUP BY COALESCE(t.conversationId, '') ORDER BY MAX(t.createdAt) DESC")
    List<Object[]> findConversations(@Param("studentId") Long studentId);
}
