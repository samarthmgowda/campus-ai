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
        Long alumniId = null;
        if (body != null) {
            Object alumniIdObj = body.get("alumniId");
            if (alumniIdObj instanceof Number) {
                alumniId = ((Number) alumniIdObj).longValue();
            } else if (alumniIdObj instanceof String) {
                try {
                    alumniId = Long.valueOf((String) alumniIdObj);
                } catch (NumberFormatException ignored) {}
            }
            if (alumniId == null) {
                Object idObj = body.get("id");
                if (idObj instanceof Number) {
                    alumniId = ((Number) idObj).longValue();
                } else if (idObj instanceof String) {
                    try {
                        alumniId = Long.valueOf((String) idObj);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        String message = null;
        if (body != null && body.get("message") instanceof String) {
            message = (String) body.get("message");
        }
        String company = null;
        if (body != null && body.get("company") instanceof String) {
            company = (String) body.get("company");
        }
        String role = null;
        if (body != null && body.get("role") instanceof String) {
            role = (String) body.get("role");
        }
        return referralService.request(studentId, alumniId, message, company, role);
    }

    /** POST /referrals/{id}/respond {action: ACCEPT|REJECT} (student owner) */
    @PostMapping("/referrals/{id}/respond")
    public ReferralService.ReferralDto respond(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
        String action = null;
        if (body != null) {
            Object actionObj = body.get("action");
            if (actionObj instanceof String) {
                action = (String) actionObj;
            }
            if (action == null) {
                Object statusObj = body.get("status");
                if (statusObj instanceof String) {
                    action = (String) statusObj;
                }
            }
        }
        if (action == null) action = "ACCEPT";
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
        Long alumniId = null;
        if (body != null) {
            Object alumniIdObj = body.get("alumniId");
            if (alumniIdObj instanceof Number) {
                alumniId = ((Number) alumniIdObj).longValue();
            } else if (alumniIdObj instanceof String) {
                try {
                    alumniId = Long.valueOf((String) alumniIdObj);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        String action = "ACCEPT";
        if (body != null && body.get("action") instanceof String) {
            action = (String) body.get("action");
        }
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