package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DashboardService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Role-based dashboards (Phase 2 M5).
 * The composite X-Demo-User header ("role:id") determines which dashboard is returned.
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
        DemoIdentity identity = requireIdentity(header);
        if (!identity.isStudent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student dashboard requires a student login");
        }
        return dashboardService.getStudentDashboard(identity.id());
    }

    /**
     * Alumni dashboard — mentorship requests, referrals, connections,
     * skill graph snapshot.
     * GET /dashboard/alumni?alumniId=1
     *
     * alumniId falls back to the logged-in alumni when the header carries an
     * alumni identity, so an alumni login works with no query param.
     */
    @GetMapping("/alumni")
    public Map<String, Object> alumniDashboard(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) Long alumniId) {
        DemoIdentity identity = DemoIdentity.parse(header);
        Long effectiveAlumniId = alumniId;
        Long adminAsUserId = null;

        if (effectiveAlumniId == null && identity != null && identity.isAlumni()) {
            effectiveAlumniId = identity.id();
        }
        if (identity != null && identity.isStudent()) {
            adminAsUserId = identity.id();
        }
        if (effectiveAlumniId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "alumniId required (or log in as an alumni)");
        }
        return dashboardService.getAlumniDashboard(effectiveAlumniId, adminAsUserId);
    }

    /**
     * Recruiter dashboard — postings, applications, notifications.
     * GET /dashboard/recruiter
     */
    @GetMapping("/recruiter")
    public Map<String, Object> recruiterDashboard(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        DemoIdentity identity = requireIdentity(header);
        if (!identity.isRecruiter()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recruiter dashboard requires a recruiter login");
        }
        return dashboardService.getRecruiterDashboard(identity.id());
    }

    private DemoIdentity requireIdentity(String header) {
        try {
            return DemoIdentity.require(header);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
