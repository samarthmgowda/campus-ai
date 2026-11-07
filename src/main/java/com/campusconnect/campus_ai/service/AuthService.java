package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Recruiter;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.entity.User;
import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.RecruiterRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Authentication Service for real accounts.
 *
 * Handles sign-up (bcrypt hashing), login (token issuance), and user lookup.
 * Self-service sign-up is limited to STUDENT / ALUMNI / RECRUITER; ADMIN is
 * provisioned out-of-band (see AdminBootstrap).
 *
 * Sign-up also creates the role-appropriate profile row (Student/Alumni/
 * Recruiter) and binds it to the account. Every acting-user lookup in the app
 * resolves a profile id from the JWT, so an account with no bound profile
 * would be unable to read its own dashboard — creating the row here keeps
 * sign-up and the rest of the system consistent.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final RecruiterRepository recruiterRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository,
                       StudentRepository studentRepository,
                       AlumniRepository alumniRepository,
                       RecruiterRepository recruiterRepository,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.recruiterRepository = recruiterRepository;
        this.jwtService = jwtService;
    }

    /**
     * Create an account and its bound profile row, returning both the token
     * and the account so the caller can persist a session in one round-trip.
     */
    @Transactional
    public AuthResult signUp(String email, String password, String fullName, UserRole role, Long profileId) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        validatePassword(password);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        if (role == UserRole.ADMIN) {
            throw new IllegalArgumentException("Admin accounts cannot be self-registered");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Bind an existing profile row when one was supplied; otherwise create a
        // fresh one so the account has something to act as.
        Long resolvedProfileId = profileId != null ? profileId : createProfile(role, fullName, normalizedEmail);

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                role,
                fullName.trim(),
                resolvedProfileId);
        user.setEnabled(true);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Unique-email race: two signups for the same address at once.
            throw new IllegalArgumentException("Email already registered");
        }

        return new AuthResult(jwtService.createToken(user), user);
    }

    /** Verify credentials and issue a token. */
    public AuthResult login(String email, String password) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail == null ? "" : normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is disabled");
        }

        return new AuthResult(jwtService.createToken(user), user);
    }

    public User me(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    /**
     * Create an admin if none exists. Used at startup by AdminBootstrap and
     * by tests. Idempotent on email.
     */
    @Transactional
    public User ensureAdmin(String email, String password, String fullName) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmail(normalizedEmail == null ? "" : normalizedEmail);
        if (existing.isPresent()) return existing.get();

        User admin = new User(normalizedEmail, passwordEncoder.encode(password), UserRole.ADMIN, fullName, null);
        admin.setEnabled(true);
        return userRepository.save(admin);
    }

    // ——— helpers ———

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    /** Create a minimal profile row for a freshly signed-up account. */
    private Long createProfile(UserRole role, String fullName, String email) {
        String name = fullName.trim();
        if (role == UserRole.STUDENT) {
            return studentRepository.save(new Student(name, null, null, null, null, null, null, null, "AVAILABLE")).getId();
        }
        if (role == UserRole.ALUMNI) {
            return alumniRepository.save(new Alumni(name, null, null, null, null, null, null, null, "MENTORING")).getId();
        }
        if (role == UserRole.RECRUITER) {
            return recruiterRepository.save(new Recruiter(name, null, null, email, null, null)).getId();
        }
        // ADMIN never reaches here (rejected above), but keep the signature total.
        return null;
    }

    /** A successful authentication: the signed token plus the account it belongs to. */
    public record AuthResult(String token, User user) {}
}
