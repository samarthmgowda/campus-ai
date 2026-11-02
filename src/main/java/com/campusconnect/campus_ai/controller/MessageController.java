package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.MessageService;
import com.campusconnect.campus_ai.service.DemoIdentity;
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

    /** Send a DM. POST /messages {recipientId, content} with X-Demo-User */
    @PostMapping
    public MessageService.MessageDto send(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long senderId = resolve(header, body, "senderId");
        Long recipientId = toLong(body.get("recipientId"));
        if (recipientId == null) recipientId = toLong(body.get("peerId"));
        if (recipientId == null) throw new IllegalArgumentException("recipientId is required");
        String content = body.get("content") instanceof String s ? s : "";
        return messageService.send(senderId, recipientId, content);
    }

    /** Thread with a peer. GET /messages/thread/{peerId} with X-Demo-User */
    @GetMapping("/thread/{peerId}")
    public List<MessageService.MessageDto> thread(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long peerId) {
        Long viewerId = requireHeader(header);
        return messageService.getThread(viewerId, peerId);
    }

    /** Mark thread as read. POST /messages/thread/{peerId}/read with X-Demo-User */
    @PostMapping("/thread/{peerId}/read")
    public Map<String, Object> markRead(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @PathVariable Long peerId) {
        Long viewerId = requireHeader(header);
        long marked = messageService.markThreadRead(viewerId, peerId);
        return Map.of("peerId", peerId, "markedRead", marked);
    }

    /** Inbox grouped by peer. GET /messages/inbox with X-Demo-User */
    @GetMapping("/inbox")
    public List<MessageService.ConversationDto> inbox(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long viewerId = requireHeader(header);
        return messageService.getInbox(viewerId);
    }

    /** Unread count. GET /messages/unread-count with X-Demo-User */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long viewerId = requireHeader(header);
        long count = messageService.getUnreadCount(viewerId);
        return Map.of("userId", viewerId, "unreadCount", count);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }

    private Long resolve(String header, Map<String, Object> body, String bodyKey) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        if (body != null) {
            Long v = toLong(body.get(bodyKey));
            if (v != null) return v;
            v = toLong(body.get("authorId"));
            if (v != null) return v;
        }
        throw new IllegalArgumentException("Sender required (X-Demo-User header or " + bodyKey + " in body)");
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
