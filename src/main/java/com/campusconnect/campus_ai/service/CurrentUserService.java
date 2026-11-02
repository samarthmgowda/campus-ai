package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the acting demo user from the X-Demo-User header.
 *
 * The header is composite — {@code "role:id"} (e.g. {@code "student:1"},
 * {@code "alumni:3"}) — because Student, Alumni and Recruiter rows live in
 * separate tables with independent auto-increment IDs, so a bare number is
 * ambiguous. A bare number is still interpreted as a student for backward
 * compatibility (see {@link DemoIdentity}).
 */
@Service
public class CurrentUserService {

    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final RecruiterRepository recruiterRepository;

    public CurrentUserService(StudentRepository studentRepository,
                              AlumniRepository alumniRepository,
                              RecruiterRepository recruiterRepository) {
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.recruiterRepository = recruiterRepository;
    }

    /** Student entity for a student id; used by student-only flows. */
    public Student requireUser(Long studentId) {
        if (studentId == null) throw new IllegalArgumentException("Unknown demo user");
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown demo user"));
    }

    /** Role-aware profile header for any of the three roles. */
    public Map<String, Object> resolveProfile(String header) {
        DemoIdentity identity = DemoIdentity.require(header);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("role", identity.role());
        profile.put("id", identity.id());

        switch (identity.role()) {
            case DemoIdentity.ROLE_STUDENT -> {
                Student s = studentRepository.findById(identity.id())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown demo user: " + header));
                profile.put("name", s.getName());
                profile.put("branch", s.getBranch());
                profile.put("year", s.getYear());
                profile.put("skills", s.getSkills());
                profile.put("interests", s.getInterests());
                profile.put("bio", s.getBio());
                profile.put("githubUrl", s.getGithubUrl());
                profile.put("linkedinUrl", s.getLinkedinUrl());
                profile.put("availability", s.getAvailability());
            }
            case DemoIdentity.ROLE_ALUMNI -> {
                Alumni a = alumniRepository.findById(identity.id())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown demo user: " + header));
                profile.put("name", a.getName());
                profile.put("branch", a.getBranch());
                profile.put("graduationYear", a.getGraduationYear());
                profile.put("skills", a.getSkills());
                profile.put("currentRole", a.getCurrentRole());
                profile.put("currentCompany", a.getCurrentCompany());
                profile.put("bio", a.getBio());
                profile.put("linkedinUrl", a.getLinkedinUrl());
                profile.put("availability", a.getAvailability());
            }
            case DemoIdentity.ROLE_RECRUITER -> {
                Recruiter r = recruiterRepository.findById(identity.id())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown demo user: " + header));
                profile.put("name", r.getName());
                profile.put("company", r.getCompany());
                profile.put("roleTitle", r.getRole());
                profile.put("email", r.getEmail());
                profile.put("linkedinUrl", r.getLinkedinUrl());
                profile.put("bio", r.getBio());
            }
            default -> throw new IllegalArgumentException("Unknown demo user: " + header);
        }
        return profile;
    }

    public List<Student> listAll() {
        return studentRepository.findAll();
    }
}
