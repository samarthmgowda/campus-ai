package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.NotificationService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for notification workflows: list, list unread, mark read, mark all read.
 * Triggers: "notification", "notifications", "alert", "mark read", "unread"
 */
@Component
public class NotificationTool implements Tool {

    private final NotificationService notificationService;

    public NotificationTool(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String name() {
        return "notificationManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"notification", "notifications", "alert", "alerts", "mark read", "read", "unread"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "View your notifications (all or unread only), mark individual notifications as read, or mark all as read.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("mark all") || q.equals("mark all read") || q.contains("mark all read")) {
            params.put("action", "markAllRead");
        } else if (q.contains("mark read") || q.startsWith("read ")) {
            params.put("action", "markRead");
            Long notifId = extractNumberAfter(intent, "mark read |read ");
            params.put("notificationId", notifId);
        } else if (q.contains("unread")) {
            params.put("action", "listUnread");
        } else {
            params.put("action", "list");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("list".equals(action)) {
            var notifs = notificationService.getForUser(userId, false, 20);
            if (notifs.isEmpty()) {
                return ToolResult.success("You have no notifications.", Map.of());
            }
            StringBuilder sb = new StringBuilder();
            long unreadCount = notificationService.getUnreadCount(userId);
            sb.append("Notifications (").append(notifs.size()).append(" shown, ").append(unreadCount).append(" unread):\n");
            for (var n : notifs) {
                sb.append("  ").append(n.readFlag() ? "[read]" : "[NEW]").append(" ")
                        .append("#").append(n.id()).append(" ")
                        .append(n.type()).append(": ").append(n.title());
                if (n.body() != null && !n.body().isBlank()) {
                    sb.append(" — ").append(n.body());
                }
                sb.append(" [").append(n.createdAt()).append("]\n");
            }
            sb.append("\nMark read: POST /notifications/{id}/read\n")
              .append("Mark all read: POST /notifications/read-all");
            return ToolResult.success(sb.toString(), Map.of("total", notifs.size(), "unread", unreadCount));
        }

        if ("listUnread".equals(action)) {
            var notifs = notificationService.getForUser(userId, true, 0);
            if (notifs.isEmpty()) {
                return ToolResult.success("You have no unread notifications.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Unread notifications:\n");
            for (var n : notifs) {
                sb.append("  [NEW] #").append(n.id()).append(" ").append(n.type())
                        .append(": ").append(n.title());
                if (n.body() != null && !n.body().isBlank()) {
                    sb.append(" — ").append(n.body());
                }
                sb.append(" [").append(n.createdAt()).append("]\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", notifs.size()));
        }

        if ("markRead".equals(action)) {
            Long notifId = (Long) params.get("notificationId");
            if (notifId == null) {
                return ToolResult.clarification(
                        "Which notification? Try: \"mark read 5\" (notification ID)");
            }
            try {
                var result = notificationService.markRead(notifId, userId);
                return ToolResult.success("Notification #" + notifId + " marked as read.", result);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot mark read", e.getMessage());
            }
        }

        if ("markAllRead".equals(action)) {
            var result = notificationService.markAllRead(userId);
            return ToolResult.success("All notifications marked as read.", result);
        }

        return ToolResult.error("Unknown notification action", "Action: " + action);
    }

    private Long extractNumberAfter(String text, String pattern) {
        String lower = text.toLowerCase(Locale.ROOT);
        String p = pattern.trim();
        int idx = lower.indexOf(p);
        if (idx >= 0) {
            String rest = text.substring(idx + p.length()).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(rest);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}