package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.FollowService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for follow/unfollow workflows and viewing followers/following.
 * Triggers: "follow", "unfollow", "following", "followers"
 */
@Component
public class FollowTool implements Tool {

    private final FollowService followService;

    public FollowTool(FollowService followService) {
        this.followService = followService;
    }

    @Override
    public String name() {
        return "followManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"follow", "unfollow", "following", "followers", "who i follow", "who follows me"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Follow/unfollow peers, view who you follow, and see your followers.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.startsWith("unfollow ") || q.contains(" unfollow ")) {
            params.put("action", "unfollow");
            Long targetId = extractNumberAfter(intent, "unfollow ");
            params.put("targetId", targetId);
        } else if (q.startsWith("follow ") || q.contains(" follow ")) {
            params.put("action", "follow");
            Long targetId = extractNumberAfter(intent, "follow ");
            params.put("targetId", targetId);
        } else if (q.contains("who i follow") || q.contains("my following") || q.equals("following")) {
            params.put("action", "getFollowing");
        } else if (q.contains("who follows me") || q.contains("my followers") || q.equals("followers")) {
            params.put("action", "getFollowers");
        } else {
            params.put("action", "stats");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("follow".equals(action)) {
            Long targetId = (Long) params.get("targetId");
            if (targetId == null) {
                return ToolResult.clarification(
                        "Who would you like to follow? Try: \"follow 5\" (user ID).");
            }
            try {
                var result = followService.follow(userId, targetId);
                String status = (String) result.get("status");
                if ("already_following".equals(status)) {
                    return ToolResult.success("You already follow this user.", result);
                }
                return ToolResult.success("You are now following user " + targetId + ".", result);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot follow", e.getMessage());
            }
        }

        if ("unfollow".equals(action)) {
            Long targetId = (Long) params.get("targetId");
            if (targetId == null) {
                return ToolResult.clarification(
                        "Who would you like to unfollow? Try: \"unfollow 5\" (user ID).");
            }
            try {
                var result = followService.unfollow(userId, targetId);
                return ToolResult.success("You unfollowed user " + targetId + ".", result);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot unfollow", e.getMessage());
            }
        }

        if ("getFollowing".equals(action)) {
            var following = followService.getFollowing(userId);
            if (following.isEmpty()) {
                return ToolResult.success("You don't follow anyone yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("You follow:\n");
            for (var f : following) {
                sb.append("  - ").append(f.name()).append(" (ID: ").append(f.studentId())
                        .append(", ").append(f.branch()).append(" Y").append(f.year())
                        .append(") — ").append(f.skills()).append("\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", following.size()));
        }

        if ("getFollowers".equals(action)) {
            var followers = followService.getFollowers(userId);
            if (followers.isEmpty()) {
                return ToolResult.success("You have no followers yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Your followers:\n");
            for (var f : followers) {
                sb.append("  - ").append(f.name()).append(" (ID: ").append(f.studentId())
                        .append(", ").append(f.branch()).append(" Y").append(f.year())
                        .append(") — ").append(f.skills()).append("\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", followers.size()));
        }

        if ("stats".equals(action)) {
            var stats = followService.getStats(userId);
            return ToolResult.success(
                    "Following: " + stats.get("followingCount") + " | Followers: " + stats.get("followerCount"),
                    stats);
        }

        return ToolResult.error("Unknown follow action", "Action: " + action);
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