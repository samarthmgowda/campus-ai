package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    // GET /students?skill=Python&branch=CSE&availability=AVAILABLE
    // Any param is optional. With none, returns all.
    @GetMapping
    public List<Student> search(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String availability
    ) {
        return service.search(skill, branch, availability);
    }
}
