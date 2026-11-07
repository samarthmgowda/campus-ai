package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.service.AuthContext;
import com.campusconnect.campus_ai.service.CurrentUser;
import com.campusconnect.campus_ai.service.DashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Role-based dashboards (Phase 2 M5).
 *
 * The acting role comes from the JWT principal (see {@link AuthContext}), not
 * from a client-supplied header — a recruiter can no longer ask for the student
 * dashboard by typing a different header value.
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
    public Map<String, Object> studentDashboard() {
        CurrentUser cu = AuthContext.require();
        if (cu.role() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student dashboard requires a student login");
        }
        return dashboardService.getStudentDashboard(cu.actingId());
    }

    /**
     * Alumni dashboard — mentorship requests, referrals, connections,
     * skill graph snapshot.
     * GET /dashboard/alumni?alumniId=1
     *
     * An alumni login needs no query param: the principal supplies the id.
     * A student may view another alumni's dashboard by passing alumniId; the
     * student id then rides along as the acting context.
     */
    @GetMapping("/alumni")
    public Map<String, Object> alumniDashboard(@RequestParam(required = false) Long alumniId) {
        CurrentUser cu = AuthContext.require();
        Long effectiveAlumniId = alumniId;
        Long adminAsUserId = null;

        if (effectiveAlumniId == null && cu.role() == UserRole.ALUMNI) {
            effectiveAlumniId = cu.actingId();
        }
        if (cu.role() == UserRole.STUDENT) {
            adminAsUserId = cu.actingId();
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
    public Map<String, Object> recruiterDashboard() {
        CurrentUser cu = AuthContext.require();
        if (cu.role() != UserRole.RECRUITER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recruiter dashboard requires a recruiter login");
        }
        return dashboardService.getRecruiterDashboard(cu.actingId());
    }
}
