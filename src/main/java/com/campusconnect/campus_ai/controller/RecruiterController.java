package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.RecruiterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recruiter")
public class RecruiterController {

    private final RecruiterService recruiterService;

    public RecruiterController(RecruiterService recruiterService) {
        this.recruiterService = recruiterService;
    }

    /**
     * Discovery: find students by skill/availability/year.
     * GET /recruiter/students?skill=&availability=&minYear=&maxYear=
     */
    @GetMapping("/students")
    public List<RecruiterService.StudentResult> searchStudents(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) Integer minYear,
            @RequestParam(required = false) Integer maxYear) {
        return recruiterService.searchStudents(skill, availability, minYear, maxYear);
    }

    /**
     * Portfolio: projects where student is a member.
     * GET /recruiter/students/{studentId}/portfolio
     */
    @GetMapping("/students/{studentId}/portfolio")
    public List<RecruiterService.ProjectPortfolioEntry> getPortfolio(@PathVariable Long studentId) {
        return recruiterService.getStudentPortfolio(studentId);
    }

    /**
     * Full detail: profile + portfolio in one call.
     * GET /recruiter/students/{studentId}
     */
    @GetMapping("/students/{studentId}")
    public Map<String, Object> getStudentDetail(@PathVariable Long studentId) {
        return recruiterService.getStudentDetail(studentId);
    }
}