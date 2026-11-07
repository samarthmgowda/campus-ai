package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.AuthContext;
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

 /** POST /recruiter/postings {title, description, requiredSkills} as recruiterId */
    @PostMapping
    public RecruiterPostingService.PostingDto create(
            @RequestBody Map<String, Object> body) {
        Long recruiterId = AuthContext.id();
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

    /** POST /recruiter/postings/{id}/close (owner) */
    @PostMapping("/{id}/close")
    public RecruiterPostingService.PostingDto close(@PathVariable Long id) {
        Long recruiterId = AuthContext.id();
        return postingService.closePosting(recruiterId, id);
    }

 /** POST /recruiter/postings/{id}/apply as studentId */
    @PostMapping("/{id}/apply")
    public RecruiterPostingService.ApplicationDto apply(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long studentId = AuthContext.id();
        return postingService.apply(studentId, id);
    }

    /** POST /recruiter/postings/{id}/applications/{appId}/decide {action: SHORTLIST|REJECT} — recruiter */
    @PostMapping("/{id}/applications/{appId}/decide")
    public RecruiterPostingService.ApplicationDto decide(
            @PathVariable Long id,
            @PathVariable Long appId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long recruiterId = AuthContext.id();
        String action = body != null && body.get("action") instanceof String a ? a : null;
        if (action == null && body != null && body.get("status") instanceof String s) action = s;
        return postingService.decide(recruiterId, id, appId, action);
    }

    /** GET /recruiter/postings/{id}/applications?status= — recruiter inbox */
    @GetMapping("/{id}/applications")
    public List<RecruiterPostingService.ApplicationDto> applications(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {
        Long recruiterId = AuthContext.id();
        return postingService.getApplicationsForPosting(recruiterId, id, status);
    }

}
