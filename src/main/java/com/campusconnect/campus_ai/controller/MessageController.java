package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.MessageService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

 /** Send a DM. POST /messages {recipientId, content} */
    @PostMapping
    public MessageService.MessageDto send(
            @RequestBody Map<String, Object> body) {
        Long senderId = AuthContext.id();
        Long recipientId = toLong(body.get("recipientId"));
        if (recipientId == null) recipientId = toLong(body.get("peerId"));
        if (recipientId == null) throw new IllegalArgumentException("recipientId is required");
        String content = body.get("content") instanceof String s ? s : "";
        return messageService.send(senderId, recipientId, content);
    }

 /** Thread with a peer. GET /messages/thread/{peerId} */
    @GetMapping("/thread/{peerId}")
    public List<MessageService.MessageDto> thread(
            @PathVariable Long peerId) {
        Long viewerId = AuthContext.id();
        return messageService.getThread(viewerId, peerId);
    }

 /** Mark thread as read. POST /messages/thread/{peerId}/read */
    @PostMapping("/thread/{peerId}/read")
    public Map<String, Object> markRead(
            @PathVariable Long peerId) {
        Long viewerId = AuthContext.id();
        long marked = messageService.markThreadRead(viewerId, peerId);
        return Map.of("peerId", peerId, "markedRead", marked);
    }

    /** Inbox grouped by peer. GET /messages/inbox */
    @GetMapping("/inbox")
    public List<MessageService.ConversationDto> inbox() {
        Long viewerId = AuthContext.id();
        return messageService.getInbox(viewerId);
    }

    /** Unread count. GET /messages/unread-count */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        Long viewerId = AuthContext.id();
        long count = messageService.getUnreadCount(viewerId);
        return Map.of("userId", viewerId, "unreadCount", count);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
