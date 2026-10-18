package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recruiter/applications")
public class PostingApplicationController {

    private final RecruiterPostingService postingService;

    public PostingApplicationController(RecruiterPostingService postingService) {
        this.postingService = postingService;
    }

    /** GET /recruiter/applications/my?status= with X-Demo-User as studentId */
    @GetMapping("/my")
    public List<RecruiterPostingService.ApplicationDto> my(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(required = false) String status) {
        Long studentId = requireHeader(header);
        return postingService.getMyApplications(studentId, status);
    }

    /** POST /recruiter/applications/{id}/withdraw with X-Demo-User */
    @PostMapping("/{id}/withdraw")
    public Map<String, Object> withdraw(
            @PathVariable Long id,
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long studentId = requireHeader(header);
        return postingService.withdraw(studentId, id);
    }

    private Long requireHeader(String header) {
        if (header != null && !header.isBlank()) try { return Long.valueOf(header.trim()); } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
