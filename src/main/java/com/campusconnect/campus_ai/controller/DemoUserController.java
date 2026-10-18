package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/demo")
public class DemoUserController {

    private final CurrentUserService currentUserService;

    public DemoUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return currentUserService.listAll().stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("branch", s.getBranch());
                    m.put("year", s.getYear());
                    return m;
                }).collect(Collectors.toList());
    }
}
