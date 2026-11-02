package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.AnalyticsService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for analytics: platform summary, top skills, project stats, community stats.
 * Triggers: "analytics", "stats", "statistics", "dashboard", "top skills"
 */
@Component
public class AnalyticsTool implements Tool {

    private final AnalyticsService analyticsService;

    public AnalyticsTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String name() {
        return "analyticsViewer";
    }

    @Override
    public String[] triggers() {
        return new String[]{"analytics", "stats", "statistics", "dashboard", "platform stats", "top skills", "insights", "metrics"};
    }

    @Override
    public boolean requiresAuth() {
        return false;
    }

    @Override
    public String description() {
        return "View platform-wide analytics: summary stats, top skills, project breakdowns, and community insights.";
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("top skill")) {
            params.put("action", "topSkills");
        } else if (q.contains("project")) {
            params.put("action", "projectStats");
        } else if (q.contains("community")) {
            params.put("action", "communityStats");
        } else if (q.contains("branch") || q.contains("availability")) {
            params.put("action", q.contains("branch") ? "branchBreakdown" : "availabilityBreakdown");
        } else {
            params.put("action", "summary");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("summary".equals(action)) {
            var s = analyticsService.getSummary();
            StringBuilder sb = new StringBuilder("Platform summary:\n");
            s.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            return ToolResult.success(sb.toString(), s);
        }

        if ("topSkills".equals(action)) {
            var skills = analyticsService.topSkills(10);
            if (skills.isEmpty()) {
                return ToolResult.success("No skills data yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Top skills on campus:\n");
            int show = Math.min(10, skills.size());
            for (int i = 0; i < show; i++) {
                var s = skills.get(i);
                sb.append("  ").append(i + 1).append(". ").append(s.get("skill"))
                        .append(" (").append(s.get("count")).append(" students)\n");
            }
            return ToolResult.success(sb.toString(), Map.of("skills", skills));
        }

        if ("projectStats".equals(action)) {
            var stats = analyticsService.projectStats();
            StringBuilder sb = new StringBuilder("Project stats:\n");
            sb.append("  Total: ").append(stats.get("total")).append("\n");
            sb.append("  Avg members: ").append(stats.get("avgMembers")).append("\n");
            sb.append("  By status: ").append(stats.get("byStatus")).append("\n");
            sb.append("  By domain: ").append(stats.get("byDomain")).append("\n");
            return ToolResult.success(sb.toString(), stats);
        }

        if ("communityStats".equals(action)) {
            var stats = analyticsService.communityStats();
            StringBuilder sb = new StringBuilder("Community stats:\n");
            sb.append("  Posts: ").append(stats.get("posts")).append("\n");
            sb.append("  Likes: ").append(stats.get("likes")).append("\n");
            sb.append("  Comments: ").append(stats.get("comments")).append("\n");
            sb.append("  Messages: ").append(stats.get("messages")).append("\n");
            sb.append("  Reviews: ").append(stats.get("reviews")).append("\n");
            var topPosts = (List<?>) stats.getOrDefault("topPosts", List.of());
            if (!topPosts.isEmpty()) {
                sb.append("  Top posts:\n");
                topPosts.forEach(tp -> sb.append("    - ").append(tp).append("\n"));
            }
            return ToolResult.success(sb.toString(), stats);
        }

        if ("branchBreakdown".equals(action)) {
            var breakdown = analyticsService.branchBreakdown();
            var data = new HashMap<String, Object>();
            breakdown.forEach((k, v) -> data.put(k, v));
            return ToolResult.success("Students by branch: " + breakdown, data);
        }

        if ("availabilityBreakdown".equals(action)) {
            var breakdown = analyticsService.availabilityBreakdown();
            var data = new HashMap<String, Object>();
            breakdown.forEach((k, v) -> data.put(k, v));
            return ToolResult.success("Availability breakdown: " + breakdown, data);
        }

        return ToolResult.error("Unknown analytics action", "Action: " + action);
    }
}