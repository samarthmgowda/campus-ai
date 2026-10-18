package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.service.AlumniService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alumni")
public class AlumniController {

    private final AlumniService alumniService;

    public AlumniController(AlumniService alumniService) {
        this.alumniService = alumniService;
    }

    @GetMapping
    public List<Map<String, Object>> search(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String availability) {
        return alumniService.search(skill, availability).stream().map(a -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("branch", a.getBranch());
            m.put("graduationYear", a.getGraduationYear());
            m.put("skills", a.getSkills());
            m.put("currentRole", a.getCurrentRole());
            m.put("currentCompany", a.getCurrentCompany());
            m.put("availability", a.getAvailability());
            return m;
        }).collect(Collectors.toList());
    }
}