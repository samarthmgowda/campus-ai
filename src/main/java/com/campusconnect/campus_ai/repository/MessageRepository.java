package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :a AND m.recipient.id = :b) OR (m.sender.id = :b AND m.recipient.id = :a) ORDER BY m.createdAt ASC, m.id ASC")
    List<Message> findThread(Long a, Long b);

    @Query("SELECT m FROM Message m WHERE m.sender.id = :userId OR m.recipient.id = :userId ORDER BY m.createdAt DESC")
    List<Message> findAllForUser(Long userId);

    long countByRecipientIdAndReadFlagFalse(Long recipientId);

    long countBySenderIdAndRecipientIdAndReadFlagFalse(Long senderId, Long recipientId);

    List<Message> findByRecipientIdAndReadFlagFalse(Long recipientId);
}
