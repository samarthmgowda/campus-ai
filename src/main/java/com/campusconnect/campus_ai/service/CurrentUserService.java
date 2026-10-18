package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CurrentUserService {

    private final StudentRepository studentRepository;

    public CurrentUserService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public Student requireUser(Long studentId) {
        if (studentId == null) throw new IllegalArgumentException("Unknown demo user");
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown demo user"));
    }

    public List<Student> listAll() {
        return studentRepository.findAll();
    }
}
