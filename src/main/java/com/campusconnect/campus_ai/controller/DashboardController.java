package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Role-based dashboards (Phase 2 M5).
 * X-Demo-User header determines which dashboard is returned.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Student dashboard — project membership, invites, teammate requests,
     * events, connections, reputation, skill recommendations, activity.
     * GET /dashboard/student
     */
    @GetMapping("/student")
    public Map<String, Object> studentDashboard(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Demo-User header required");
        }
        try {
            Long studentId = Long.valueOf(header.trim());
            return dashboardService.getStudentDashboard(studentId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-Demo-User ID");
        }
    }

    /**
     * Alumni dashboard — mentorship requests, referrals, connections,
     * skill graph snapshot.
     * GET /dashboard/alumni?alumniId=1
     */
    @GetMapping("/alumni")
    public Map<String, Object> alumniDashboard(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam Long alumniId) {
        Long adminAsUserId = null;
        if (header != null && !header.isBlank()) try { adminAsUserId = Long.valueOf(header.trim()); } catch (NumberFormatException ignored) {}
        return dashboardService.getAlumniDashboard(alumniId, adminAsUserId);
    }

    /**
     * Recruiter dashboard — postings, applications, notifications.
     * GET /dashboard/recruiter
     */
    @GetMapping("/recruiter")
    public Map<String, Object> recruiterDashboard(
            @RequestHeader("X-Demo-User") Long recruiterId) {
        return dashboardService.getRecruiterDashboard(recruiterId);
    }
}
