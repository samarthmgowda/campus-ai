package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ReferralService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    /** POST /referrals/request {alumniId, message, company, role} with X-Demo-User */
    @PostMapping("/referrals/request")
    public ReferralService.ReferralDto request(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long studentId = requireHeader(header);
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) alumniId = body != null ? toLong(body.get("id")) : null;
        String message = body != null && body.get("message") instanceof String s ? s : null;
        String company = body != null && body.get("company") instanceof String s ? s : null;
        String role = body != null && body.get("role") instanceof String s ? s : null;
        return referralService.request(studentId, alumniId, message, company, role);
    }

    /** POST /referrals/{id}/respond {action: ACCEPT|REJECT} with X-Demo-User (student owner) */
    @PostMapping("/referrals/{id}/respond")
    public ReferralService.ReferralDto respond(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = requireHeader(header);
        String action = body != null && body.get("action") instanceof String a ? a : (body != null && body.get("status") instanceof String s ? s : "ACCEPT");
        return referralService.respond(id, studentId, action);
    }

    /** POST /referrals/{id}/withdraw with X-Demo-User (pending only) */
    @PostMapping("/referrals/{id}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return referralService.withdraw(id, studentId);
    }

    /** POST /referrals/{id}/alumni-respond {alumniId, action} — alumni-side demo without X-Demo-User */
    @PostMapping("/referrals/{id}/alumni-respond")
    public ReferralService.ReferralDto alumniRespond(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        String action = body != null && body.get("action") instanceof String a ? a : "ACCEPT";
        return referralService.alumniRespond(id, alumniId, action);
    }

    /** GET /referrals/my?status=PENDING with X-Demo-User */
    @GetMapping("/referrals/my")
    public List<ReferralService.ReferralDto> myRequests(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long studentId = requireHeader(header);
        return referralService.getMyRequests(studentId, status);
    }

    /** GET /referrals/alumni/{alumniId}?status=PENDING */
    @GetMapping("/referrals/alumni/{alumniId}")
    public List<ReferralService.ReferralDto> forAlumni(
            @PathVariable Long alumniId,
            @RequestParam(required = false) String status) {
        return referralService.getForAlumni(alumniId, status);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        try { return Long.valueOf(header.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid X-Demo-User"); }
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
