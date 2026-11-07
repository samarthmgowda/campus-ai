package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.AuthContext;
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

 /** GET /recruiter/applications/my?status= as studentId */
    @GetMapping("/my")
    public List<RecruiterPostingService.ApplicationDto> my(
            @RequestParam(required = false) String status) {
        Long studentId = AuthContext.id();
        return postingService.getMyApplications(studentId, status);
    }

    /** POST /recruiter/applications/{id}/withdraw */
    @PostMapping("/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable Long id) {
        Long studentId = AuthContext.id();
        return postingService.withdraw(studentId, id);
    }

}
