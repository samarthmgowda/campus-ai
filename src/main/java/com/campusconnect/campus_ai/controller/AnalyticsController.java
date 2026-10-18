package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** GET /analytics/summary — counts for every table (Campus Intelligence) */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return analyticsService.getSummary();
    }

    /** GET /analytics/skills?limit=10 — top skills by student count (canonical, alias-aware) */
    @GetMapping("/skills")
    public List<Map<String, Object>> topSkills(@RequestParam(defaultValue = "10") int limit) {
        int l = Math.max(1, Math.min(limit, 50));
        return analyticsService.topSkills(l);
    }

    /** GET /analytics/projects — byStatus, byDomain, avgMembers */
    @GetMapping("/projects")
    public Map<String, Object> projects() {
        return analyticsService.projectStats();
    }

    /** GET /analytics/community — posts/likes/comments/messages/reviews + top 3 liked posts */
    @GetMapping("/community")
    public Map<String, Object> community() {
        return analyticsService.communityStats();
    }

    /** GET /analytics/availability — students grouped by AVAILABLE/BUSY */
    @GetMapping("/availability")
    public Map<String, Long> availability() {
        return analyticsService.availabilityBreakdown();
    }

    /** GET /analytics/branches — students grouped by branch */
    @GetMapping("/branches")
    public Map<String, Long> branches() {
        return analyticsService.branchBreakdown();
    }
}
