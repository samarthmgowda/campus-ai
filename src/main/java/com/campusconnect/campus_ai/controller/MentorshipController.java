package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.MentorshipService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class MentorshipController {

    private final MentorshipService mentorshipService;

    public MentorshipController(MentorshipService mentorshipService) {
        this.mentorshipService = mentorshipService;
    }

 /** POST /mentorship/request {alumniId, message} */
    @PostMapping("/mentorship/request")
    public MentorshipService.MentorshipDto request(
            @RequestBody Map<String, Object> body) {
        Long studentId = AuthContext.id();
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) alumniId = body != null ? toLong(body.get("id")) : null;
        String message = body != null && body.get("message") instanceof String s ? s : null;
        return mentorshipService.request(studentId, alumniId, message);
    }

 /** POST /mentorship/{id}/respond {action: ACCEPT|REJECT} (student owner) */
    @PostMapping("/mentorship/{id}/respond")
    public MentorshipService.MentorshipDto respond(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
        String action = body != null && body.get("action") instanceof String a ? a : (body != null && body.get("status") instanceof String s ? s : "ACCEPT");
        return mentorshipService.respond(id, studentId, action);
    }

    /** POST /mentorship/{id}/withdraw (pending only) */
    @PostMapping("/mentorship/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable Long id) {
        Long studentId = AuthContext.id();
        return mentorshipService.withdraw(id, studentId);
    }

 /** POST /mentorship/{id}/alumni-respond {action} — alumni-side demo without a signed-in user, body {alumniId, action} */
    @PostMapping("/mentorship/{id}/alumni-respond")
    public MentorshipService.MentorshipDto alumniRespond(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        String action = body != null && body.get("action") instanceof String a ? a : "ACCEPT";
        return mentorshipService.alumniRespond(id, alumniId, action);
    }

 /** GET /mentorship/my?status=PENDING */
    @GetMapping("/mentorship/my")
    public List<MentorshipService.MentorshipDto> myRequests(
            @RequestParam(required = false) String status) {
        Long studentId = AuthContext.id();
        return mentorshipService.getMyRequests(studentId, status);
    }

    /** GET /mentorship/alumni/{alumniId}?status=PENDING */
    @GetMapping("/mentorship/alumni/{alumniId}")
    public List<MentorshipService.MentorshipDto> forAlumni(
            @PathVariable Long alumniId,
            @RequestParam(required = false) String status) {
        return mentorshipService.getForAlumni(alumniId, status);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
