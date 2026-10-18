package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Notification;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.NotificationRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               StudentRepository studentRepository) {
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
    }

    public NotificationDto notify(Long recipientId, String type, String title, String body, String refType, Long refId) {
        if (recipientId == null) throw new IllegalArgumentException("recipientId required");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type required");
        Student recipient = studentRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + recipientId));
        Notification n = new Notification(recipient, type.trim().toUpperCase(), title, body, refType, refId);
        n = notificationRepository.save(n);
        return toDto(n);
    }

    /** Notify without self-noise; returns empty if self-targeted. */
    public NotificationDto notifyOther(Long recipientId, Long actorId, String type, String title, String body, String refType, Long refId) {
        if (recipientId != null && recipientId.equals(actorId)) return null;
        return notify(recipientId, type, title, body, refType, refId);
    }

    public List<NotificationDto> getForUser(Long userId, boolean unreadOnly, int limit) {
        studentRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<Notification> list = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
        if (limit > 0 && list.size() > limit) list = list.subList(0, limit);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFlagFalse(userId);
    }

    public Map<String, Object> markRead(Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!n.getRecipient().getId().equals(userId))
            throw new IllegalArgumentException("Not your notification");
        n.setReadFlag(true);
        notificationRepository.save(n);
        return Map.of("id", n.getId(), "readFlag", true);
    }

    public Map<String, Object> markAllRead(Long userId) {
        var unread = notificationRepository.findByRecipientIdAndReadFlagFalseOrderByCreatedAtDesc(userId);
        for (var n : unread) n.setReadFlag(true);
        notificationRepository.saveAll(unread);
        return Map.of("markedRead", unread.size());
    }

    public record NotificationDto(Long id, Long recipientId, String recipientName, String type, String title, String body, String refType, Long refId, boolean readFlag, String createdAt) {}

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(),
                n.getRecipient() != null ? n.getRecipient().getId() : null,
                n.getRecipient() != null ? n.getRecipient().getName() : null,
                n.getType(), n.getTitle(), n.getBody(), n.getRefType(), n.getRefId(),
                n.isReadFlag(), n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
    }
}
