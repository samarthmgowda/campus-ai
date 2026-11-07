package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ReferralService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

 /** POST /referrals/request {alumniId, message, company, role} */
    @PostMapping("/referrals/request")
    public ReferralService.ReferralDto request(
            @RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.id();
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) alumniId = body != null ? toLong(body.get("id")) : null;
        String message = body != null && body.get("message") instanceof String s ? s : null;
        String company = body != null && body.get("company") instanceof String s ? s : null;
        String role = body != null && body.get("role") instanceof String s ? s : null;
        return referralService.request(studentId, alumniId, message, company, role);
    }

 /** POST /referrals/{id}/respond {action: ACCEPT|REJECT} (student owner) */
    @PostMapping("/referrals/{id}/respond")
    public ReferralService.ReferralDto respond(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
        String action = body != null && body.get("action") instanceof String a ? a : (body != null && body.get("status") instanceof String s ? s : "ACCEPT");
        return referralService.respond(id, studentId, action);
    }

    /** POST /referrals/{id}/withdraw (pending only) */
    @PostMapping("/referrals/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable Long id) {
        Long studentId = AuthContext.id();
        return referralService.withdraw(id, studentId);
    }

 /** POST /referrals/{id}/alumni-respond {alumniId, action} — alumni-side demo without a signed-in user */
    @PostMapping("/referrals/{id}/alumni-respond")
    public ReferralService.ReferralDto alumniRespond(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        String action = body != null && body.get("action") instanceof String a ? a : "ACCEPT";
        return referralService.alumniRespond(id, alumniId, action);
    }

 /** GET /referrals/my?status=PENDING */
    @GetMapping("/referrals/my")
    public List<ReferralService.ReferralDto> myRequests(
            @RequestParam(required = false) String status) {
        Long studentId = AuthContext.id();
        return referralService.getMyRequests(studentId, status);
    }

    /** GET /referrals/alumni/{alumniId}?status=PENDING */
    @GetMapping("/referrals/alumni/{alumniId}")
    public List<ReferralService.ReferralDto> forAlumni(
            @PathVariable Long alumniId,
            @RequestParam(required = false) String status) {
        return referralService.getForAlumni(alumniId, status);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
