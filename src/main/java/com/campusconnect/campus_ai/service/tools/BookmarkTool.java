package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.BookmarkService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for bookmark workflows: save, remove, list bookmarks.
 * Triggers: "bookmark", "save", "saved", "bookmarks"
 */
@Component
public class BookmarkTool implements Tool {

    private final BookmarkService bookmarkService;

    public BookmarkTool(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @Override
    public String name() {
        return "bookmarkManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"bookmark", "bookmarks", "saved", "save", "save it", "unsave", "remove bookmark"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Save/unsave bookmarks for posts, events, and projects. View your saved bookmarks.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        // Check list intent first — "my bookmarks", "show my bookmarks", "list my bookmarks"
        if (q.contains("my bookmark") || q.contains("show my bookmark") || q.contains("list my bookmark")) {
            params.put("action", "list");
        } else if (q.contains("unsave") || q.contains("remove bookmark") || q.contains("unbookmark")) {
            params.put("action", "remove");
            String refType = extractRefType(q);
            params.put("refType", refType);
            Long refId = extractNumberAfter(intent, refType != null ? refType : "");
            params.put("refId", refId);
        } else if (q.contains("save") || (q.contains("bookmark") && !q.matches(".*bookmark.*\\d+.*"))) {
            // "bookmark" alone with a ref type/number → save; bare "bookmark" → list
            if (extractRefType(q) != null && extractNumberAfter(intent, extractRefType(q)) != null) {
                params.put("action", "save");
                params.put("refType", extractRefType(q));
                params.put("refId", extractNumberAfter(intent, extractRefType(q)));
            } else {
                params.put("action", "list");
            }
        } else {
            params.put("action", "list");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("save".equals(action) || "remove".equals(action)) {
            String refType = (String) params.get("refType");
            Long refId = (Long) params.get("refId");
            if (refType == null || refId == null) {
                return ToolResult.clarification(
                        "Save or unsave? Try: \"save post 5\" or \"save event 3\" or \"remove bookmark post 5\"");
            }
            try {
                var result = bookmarkService.save(userId, refType, refId);
                String status = (String) result.get("status");
                if ("saved".equals(status)) {
                    return ToolResult.success(
                            "Saved " + refType.toLowerCase() + " #" + refId + " to your bookmarks.",
                            result);
                } else {
                    return ToolResult.success(
                            "Removed " + refType.toLowerCase() + " #" + refId + " from your bookmarks.",
                            result);
                }
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Bookmark action failed", e.getMessage());
            }
        }

        if ("list".equals(action)) {
            var all = bookmarkService.list(userId, null);
            var summary = bookmarkService.saveSummary(userId);
            if (all.isEmpty()) {
                return ToolResult.success("You have no bookmarks yet. Try: \"save post 5\"", Map.of());
            }
            StringBuilder sb = new StringBuilder("Your bookmarks:\n");
            int show = Math.min(10, all.size());
            for (int i = 0; i < show; i++) {
                var b = all.get(i);
                sb.append("  - #").append(b.id()).append(" ").append(b.refType())
                        .append(" #").append(b.refId()).append("\n");
            }
            if (all.size() > show) sb.append("  ... and ").append(all.size() - show).append(" more\n");
            sb.append("\nTotal: ").append(summary.get("total"));
            return ToolResult.success(sb.toString(), summary);
        }

        return ToolResult.error("Unknown bookmark action", "Action: " + action);
    }

    private String extractRefType(String q) {
        if (q.contains("post")) return "POST";
        if (q.contains("event")) return "EVENT";
        if (q.contains("project")) return "PROJECT";
        return null;
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