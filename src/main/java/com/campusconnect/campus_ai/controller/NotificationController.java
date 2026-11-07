package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.NotificationService;
import com.campusconnect.campus_ai.service.AuthContext;
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

 /** GET /notifications, optional ?unreadOnly=true&limit=20 */
    @GetMapping
    public List<NotificationService.NotificationDto> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = AuthContext.id();
        return notificationService.getForUser(userId, unreadOnly, Math.max(1, Math.min(limit, 100)));
    }

 /** GET /notifications/unread-count */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        Long userId = AuthContext.id();
        long c = notificationService.getUnreadCount(userId);
        return Map.of("userId", userId, "unreadCount", c);
    }

    /** POST /notifications/{id}/read — mark one as read */
    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable Long id) {
        Long userId = AuthContext.id();
        return notificationService.markRead(id, userId);
    }

    /** POST /notifications/read-all — mark all as read */
    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        Long userId = AuthContext.id();
        return notificationService.markAllRead(userId);
    }

}
