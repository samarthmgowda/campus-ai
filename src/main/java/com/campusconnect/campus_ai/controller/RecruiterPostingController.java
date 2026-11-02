package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recruiter/postings")
public class RecruiterPostingController {

    private final RecruiterPostingService postingService;

    public RecruiterPostingController(RecruiterPostingService postingService) {
        this.postingService = postingService;
    }

    /** POST /recruiter/postings {title, description, requiredSkills} with X-Demo-User as recruiterId */
    @PostMapping
    public RecruiterPostingService.PostingDto create(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long recruiterId = requireHeader(header, body);
        String title = body != null && body.get("title") instanceof String s ? s : null;
        String description = body != null && body.get("description") instanceof String s ? s : (body != null && body.get("desc") instanceof String d ? d : null);
        String requiredSkills = body != null && body.get("requiredSkills") instanceof String s ? s : (body != null && body.get("skills") instanceof String k ? k : null);
        return postingService.createPosting(recruiterId, title, description, requiredSkills);
    }

    /** GET /recruiter/postings?status=OPEN&recruiterId=1 — public list */
    @GetMapping
    public List<RecruiterPostingService.PostingDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long recruiterId) {
        return postingService.listPostings(status, recruiterId);
    }

    /** GET /recruiter/postings/{id} */
    @GetMapping("/{id}")
    public RecruiterPostingService.PostingDto get(@PathVariable Long id) {
        return postingService.getPosting(id);
    }

    /** POST /recruiter/postings/{id}/close with X-Demo-User (owner) */
    @PostMapping("/{id}/close")
    public RecruiterPostingService.PostingDto close(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long recruiterId = requireHeader(header, null);
        return postingService.closePosting(recruiterId, id);
    }

    /** POST /recruiter/postings/{id}/apply with X-Demo-User as studentId */
    @PostMapping("/{id}/apply")
    public RecruiterPostingService.ApplicationDto apply(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = requireHeader(header, body);
        return postingService.apply(studentId, id);
    }

    /** POST /recruiter/postings/{id}/applications/{appId}/decide {action: SHORTLIST|REJECT} — recruiter */
    @PostMapping("/{id}/applications/{appId}/decide")
    public RecruiterPostingService.ApplicationDto decide(
            @PathVariable Long id,
            @PathVariable Long appId,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody(required = false) Map<String, Object> body) {
        Long recruiterId = requireHeader(header, body);
        String action = body != null && body.get("action") instanceof String a ? a : null;
        if (action == null && body != null && body.get("status") instanceof String s) action = s;
        return postingService.decide(recruiterId, id, appId, action);
    }

    /** GET /recruiter/postings/{id}/applications?status= — recruiter inbox */
    @GetMapping("/{id}/applications")
    public List<RecruiterPostingService.ApplicationDto> applications(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long recruiterId = requireHeader(header, null);
        return postingService.getApplicationsForPosting(recruiterId, id, status);
    }

    private Long requireHeader(String header, Map<String, Object> body) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        if (body != null) {
            Object v = body.get("recruiterId");
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
            Object v2 = body.get("studentId");
            if (v2 instanceof Number n) return n.longValue();
            if (v2 instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
