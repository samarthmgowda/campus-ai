package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ModerationService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /** POST /reports {targetType, targetId, reason, details} */
    @PostMapping("/reports")
    public ModerationService.ReportDto report(
            @RequestBody Map<String, Object> body) {
        Long reporterId = AuthContext.id();
        String targetType;
        if (body != null && body.get("targetType") instanceof String) {
            targetType = (String) body.get("targetType");
        } else {
            targetType = "";
        }
        Long targetId = body != null ? toLong(body.get("targetId")) : null;
        String reason;
        if (body != null && body.get("reason") instanceof String) {
            reason = (String) body.get("reason");
        } else {
            reason = "OTHER";
        }
        String details;
        if (body != null && body.get("details") instanceof String) {
            details = (String) body.get("details");
        } else if (body != null && body.get("comment") instanceof String) {
            details = (String) body.get("comment");
        } else {
            details = null;
        }
        return moderationService.report(reporterId, targetType, targetId, reason, details);
    }

    /** GET /reports?status=OPEN — moderation queue */
    @GetMapping("/reports")
    public List<ModerationService.ReportDto> list(@RequestParam(defaultValue = "OPEN") String status) {
        return moderationService.list(status);
    }

    /** GET /reports/my — my reports */
    @GetMapping("/reports/my")
    public List<ModerationService.ReportDto> myReports() {
        Long reporterId = AuthContext.id();
        return moderationService.myReports(reporterId);
    }

    /** GET /reports/target/{targetType}/{targetId} */
    @GetMapping("/reports/target/{targetType}/{targetId}")
    public List<ModerationService.ReportDto> forTarget(@PathVariable String targetType, @PathVariable Long targetId) {
        return moderationService.forTarget(targetType, targetId);
    }

    /** GET /reports/stats */
    @GetMapping("/reports/stats")
    public Map<String, Object> stats() {
        return moderationService.stats();
    }

    /** POST /reports/{id}/resolve {action: RESOLVED|DISMISSED, resolution} — admin-only */
    @PostMapping("/reports/{id}/resolve")
    public ModerationService.ReportDto resolve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long actorId = AuthContext.id();
        String action;
        if (body != null && body.get("action") instanceof String) {
            action = (String) body.get("action");
        } else if (body != null && body.get("status") instanceof String) {
            action = (String) body.get("status");
        } else {
            action = "RESOLVED";
        }
        String resolution;
        if (body != null && body.get("resolution") instanceof String) {
            resolution = (String) body.get("resolution");
        } else {
            resolution = null;
        }
        return moderationService.resolve(id, actorId, action, resolution);
    }

    private Long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            String s = (String) v;
            if (!s.isBlank()) {
                try {
                    return Long.valueOf(s.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}