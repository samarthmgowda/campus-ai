package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/demo")
public class DemoUserController {

    private final CurrentUserService currentUserService;
    private final AlumniRepository alumniRepository;
    private final RecruiterRepository recruiterRepository;

    public DemoUserController(CurrentUserService currentUserService,
                              AlumniRepository alumniRepository,
                              RecruiterRepository recruiterRepository) {
        this.currentUserService = currentUserService;
        this.alumniRepository = alumniRepository;
        this.recruiterRepository = recruiterRepository;
    }

    /**
     * Lists all demo users across roles (student, alumni, recruiter).
     * Each entry includes a "role" field so the frontend can build
     * a role-aware login picker and route to the correct dashboard.
     */
    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> users = new ArrayList<>();

        for (Student s : currentUserService.listAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("role", "student");
            m.put("name", s.getName());
            m.put("branch", s.getBranch());
            m.put("year", s.getYear());
            m.put("skills", s.getSkills());
            users.add(m);
        }
        for (Alumni a : alumniRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("role", "alumni");
            m.put("name", a.getName());
            m.put("branch", a.getBranch());
            m.put("year", a.getGraduationYear());
            m.put("skills", a.getSkills());
            m.put("currentRole", a.getCurrentRole());
            m.put("currentCompany", a.getCurrentCompany());
            users.add(m);
        }
        for (Recruiter r : recruiterRepository.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("role", "recruiter");
            m.put("name", r.getName());
            m.put("company", r.getCompany());
            m.put("roleTitle", r.getRole());
            users.add(m);
        }
        return users;
    }
}