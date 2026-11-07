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
        String targetType = body != null && body.get("targetType") instanceof String s ? s : "";
        Long targetId = body != null ? toLong(body.get("targetId")) : null;
        String reason = body != null && body.get("reason") instanceof String r ? r : "OTHER";
        String details = body != null && body.get("details") instanceof String d ? d : (body != null && body.get("comment") instanceof String c ? c : null);
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
        String action = body != null && body.get("action") instanceof String a ? a : (body != null && body.get("status") instanceof String s ? s : "RESOLVED");
        String resolution = body != null && body.get("resolution") instanceof String r ? r : null;
        return moderationService.resolve(id, actorId, action, resolution);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
