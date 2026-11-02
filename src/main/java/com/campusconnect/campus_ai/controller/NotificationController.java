package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.NotificationService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /notifications with X-Demo-User, optional ?unreadOnly=true&limit=20 */
    @GetMapping
    public List<NotificationService.NotificationDto> list(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = requireHeader(header);
        return notificationService.getForUser(userId, unreadOnly, Math.max(1, Math.min(limit, 100)));
    }

    /** GET /notifications/unread-count with X-Demo-User */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        long c = notificationService.getUnreadCount(userId);
        return Map.of("userId", userId, "unreadCount", c);
    }

    /** POST /notifications/{id}/read with X-Demo-User — mark one as read */
    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable Long id,
                                        @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        return notificationService.markRead(id, userId);
    }

    /** POST /notifications/read-all with X-Demo-User — mark all as read */
    @PostMapping("/read-all")
    public Map<String, Object> markAllRead(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        return notificationService.markAllRead(userId);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }
}
