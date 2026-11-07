package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the acting user's identity and profile.
 *
 * Identity comes from the JWT principal ({@link AuthContext}) rather than the
 * retired {@code X-Demo-User} header. Student, Alumni and Recruiter rows live in
 * separate tables with independent auto-increment IDs, so the principal carries
 * both the role and the role-specific profile id.
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

    /**
     * Role-aware profile for the acting principal. Resolves against the table
     * the principal's role names, so an alumni/recruiter login reads its own row
     * instead of the student table.
     */
    public Map<String, Object> resolveProfile(CurrentUser cu) {
        UserRole profileRole = cu.role().profileRole();
        if (profileRole == null) {
            throw new IllegalArgumentException("No profile for an admin account: " + cu.email());
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("role", profileRole.token());
        profile.put("id", cu.actingId());

        switch (profileRole) {
            case STUDENT -> {
                Student s = studentRepository.findById(cu.actingId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + cu.email()));
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
            case ALUMNI -> {
                Alumni a = alumniRepository.findById(cu.actingId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + cu.email()));
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
            case RECRUITER -> {
                Recruiter r = recruiterRepository.findById(cu.actingId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + cu.email()));
                profile.put("name", r.getName());
                profile.put("company", r.getCompany());
                profile.put("roleTitle", r.getRole());
                profile.put("email", r.getEmail());
                profile.put("linkedinUrl", r.getLinkedinUrl());
                profile.put("bio", r.getBio());
            }
            default -> throw new IllegalArgumentException("Unknown role: " + cu.role());
        }
        return profile;
    }

    public List<Student> listAll() {
        return studentRepository.findAll();
    }
}
