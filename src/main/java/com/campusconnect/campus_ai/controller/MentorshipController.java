package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.MentorshipService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class MentorshipController {

    private final MentorshipService mentorshipService;

    public MentorshipController(MentorshipService mentorshipService) {
        this.mentorshipService = mentorshipService;
    }

    /** POST /mentorship/request {alumniId, message} with X-Demo-User */
    @PostMapping("/mentorship/request")
    public MentorshipService.MentorshipDto request(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long studentId = requireHeader(header);
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) alumniId = body != null ? toLong(body.get("id")) : null;
        String message = body != null && body.get("message") instanceof String s ? s : null;
        return mentorshipService.request(studentId, alumniId, message);
    }

    /** POST /mentorship/{id}/respond {action: ACCEPT|REJECT} with X-Demo-User (student owner) */
    @PostMapping("/mentorship/{id}/respond")
    public MentorshipService.MentorshipDto respond(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = requireHeader(header);
        String action = body != null && body.get("action") instanceof String a ? a : (body != null && body.get("status") instanceof String s ? s : "ACCEPT");
        return mentorshipService.respond(id, studentId, action);
    }

    /** POST /mentorship/{id}/withdraw with X-Demo-User (pending only) */
    @PostMapping("/mentorship/{id}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return mentorshipService.withdraw(id, studentId);
    }

    /** POST /mentorship/{id}/alumni-respond {action} — alumni-side demo without X-Demo-User, body {alumniId, action} */
    @PostMapping("/mentorship/{id}/alumni-respond")
    public MentorshipService.MentorshipDto alumniRespond(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long alumniId = body != null ? toLong(body.get("alumniId")) : null;
        if (alumniId == null) throw new IllegalArgumentException("alumniId required");
        String action = body != null && body.get("action") instanceof String a ? a : "ACCEPT";
        return mentorshipService.alumniRespond(id, alumniId, action);
    }

    /** GET /mentorship/my?status=PENDING with X-Demo-User */
    @GetMapping("/mentorship/my")
    public List<MentorshipService.MentorshipDto> myRequests(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long studentId = requireHeader(header);
        return mentorshipService.getMyRequests(studentId, status);
    }

    /** GET /mentorship/alumni/{alumniId}?status=PENDING */
    @GetMapping("/mentorship/alumni/{alumniId}")
    public List<MentorshipService.MentorshipDto> forAlumni(
            @PathVariable Long alumniId,
            @RequestParam(required = false) String status) {
        return mentorshipService.getForAlumni(alumniId, status);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        return null;
    }
}
