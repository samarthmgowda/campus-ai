package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Message;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.MessageRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public MessageService(MessageRepository messageRepository, StudentRepository studentRepository, NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    public MessageDto send(Long senderId, Long recipientId, String content) {
        if (senderId == null || recipientId == null)
            throw new IllegalArgumentException("Sender and recipient are required");
        if (senderId.equals(recipientId))
            throw new IllegalArgumentException("Cannot message yourself");
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Message content must not be blank");
        Student sender = studentRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found: " + senderId));
        Student recipient = studentRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found: " + recipientId));
        Message m = new Message(sender, recipient, content.trim());
        m = messageRepository.save(m);
        try {
            String preview = m.getContent().length() > 80 ? m.getContent().substring(0, 77) + "..." : m.getContent();
            notificationService.notify(recipientId, "MESSAGE", sender.getName() + " sent you a message", preview, "MESSAGE", m.getId());
        } catch (Exception ignored) {}
        return toDto(m);
    }

    public List<MessageDto> getThread(Long viewerId, Long peerId) {
        if (viewerId == null || peerId == null) throw new IllegalArgumentException("User ids required");
        studentRepository.findById(viewerId).orElseThrow(() -> new IllegalArgumentException("User not found: " + viewerId));
        studentRepository.findById(peerId).orElseThrow(() -> new IllegalArgumentException("Peer not found: " + peerId));
        return messageRepository.findThread(viewerId, peerId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public long markThreadRead(Long viewerId, Long peerId) {
        List<Message> unread = messageRepository.findThread(viewerId, peerId).stream()
                .filter(m -> m.getRecipient() != null && m.getRecipient().getId().equals(viewerId) && !m.isReadFlag())
                .toList();
        for (Message m : unread) m.setReadFlag(true);
        messageRepository.saveAll(unread);
        return unread.size();
    }

    public long getUnreadCount(Long userId) {
        return messageRepository.countByRecipientIdAndReadFlagFalse(userId);
    }

    public List<ConversationDto> getInbox(Long userId) {
        studentRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<Message> all = messageRepository.findAllForUser(userId);
        // group by peer id, keep most recent per peer
        Map<Long, Message> latestByPeer = new LinkedHashMap<>();
        Map<Long, Long> unreadByPeer = new HashMap<>();
        for (Message m : all) {
            Long peerId = m.getSender().getId().equals(userId) ? m.getRecipient().getId() : m.getSender().getId();
            latestByPeer.putIfAbsent(peerId, m);
        }
        // unread counts per peer
        for (Map.Entry<Long, Message> e : latestByPeer.entrySet()) {
            Long peerId = e.getKey();
            long unread = messageRepository.countBySenderIdAndRecipientIdAndReadFlagFalse(peerId, userId);
            unreadByPeer.put(peerId, unread);
        }
        List<ConversationDto> out = new ArrayList<>();
        for (Map.Entry<Long, Message> e : latestByPeer.entrySet()) {
            Long peerId = e.getKey();
            Message last = e.getValue();
            Student peer = studentRepository.findById(peerId).orElse(null);
            String peerName = peer != null ? peer.getName() : "Unknown";
            String preview = last.getContent();
            if (preview.length() > 80) preview = preview.substring(0, 77) + "...";
            String direction = last.getSender().getId().equals(userId) ? "out" : "in";
            out.add(new ConversationDto(peerId, peerName, last.getId(), preview, last.getCreatedAt() != null ? last.getCreatedAt().toString() : null, direction, unreadByPeer.getOrDefault(peerId, 0L)));
        }
        // already in recency order because all was DESC; LinkedHashMap preserves that
        return out;
    }

    public record MessageDto(Long id, Long senderId, String senderName, Long recipientId, String recipientName, String content, String createdAt, boolean readFlag) {}
    public record ConversationDto(Long peerId, String peerName, Long lastMessageId, String preview, String lastAt, String direction, long unreadCount) {}

    private MessageDto toDto(Message m) {
        return new MessageDto(m.getId(),
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null ? m.getSender().getName() : null,
                m.getRecipient() != null ? m.getRecipient().getId() : null,
                m.getRecipient() != null ? m.getRecipient().getName() : null,
                m.getContent(),
                m.getCreatedAt() != null ? m.getCreatedAt().toString() : null,
                m.isReadFlag());
    }
}
