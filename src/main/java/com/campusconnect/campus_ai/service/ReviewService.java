package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.Review;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.ReviewRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final StudentRepository studentRepository;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;

    public ReviewService(ReviewRepository reviewRepository,
                         StudentRepository studentRepository,
                         ProjectRepository projectRepository,
                         NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.studentRepository = studentRepository;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
    }

    public Review writeReview(Long revieweeId, Long reviewerId, Integer rating, String comment, Long projectId) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be 1–5");
        }
        if (revieweeId.equals(reviewerId)) {
            throw new IllegalArgumentException("Cannot review yourself");
        }

        Student reviewee = studentRepository.findById(revieweeId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewee not found: " + revieweeId));
        Student reviewer = studentRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found: " + reviewerId));

        Project project = null;
        if (projectId != null) {
            project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        }

        Review r = new Review(reviewee, reviewer, rating, comment, project);
        r = reviewRepository.save(r);
        try {
            notificationService.notifyOther(revieweeId, reviewerId, "REVIEW", reviewer.getName() + " reviewed you " + rating + "/5",
                    comment != null && comment.length() > 80 ? comment.substring(0, 77) + "..." : comment, "REVIEW", r.getId());
        } catch (Exception ignored) {}
        return r;
    }

    public List<ReviewDto> getReviewsForStudent(Long studentId) {
        List<Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(studentId);
        List<ReviewDto> dtos = new ArrayList<>();
        for (Review r : reviews) {
            dtos.add(toDto(r));
        }
        return dtos;
    }

    public Map<String, Object> getReputationForStudent(Long studentId) {
        List<Review> reviews = reviewRepository.findByRevieweeId(studentId);
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("studentId", studentId);
        rep.put("reviewCount", reviews.size());
        if (reviews.isEmpty()) {
            rep.put("averageRating", null);
        } else {
            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            // Round to 1 decimal
            rep.put("averageRating", Math.round(avg * 10) / 10.0);
        }
        return rep;
    }

    public List<ReviewDto> getReviewsForProject(Long projectId) {
        List<Review> reviews = reviewRepository.findByProjectId(projectId);
        List<ReviewDto> dtos = new ArrayList<>();
        for (Review r : reviews) dtos.add(toDto(r));
        return dtos;
    }

    // ——— DTO ———

    public record ReviewDto(Long id, Long revieweeId, String revieweeName, Long reviewerId, String reviewerName,
                             Integer rating, String comment, Long projectId, String projectName, String createdAt) {}

    private ReviewDto toDto(Review r) {
        return new ReviewDto(
                r.getId(),
                r.getReviewee() != null ? r.getReviewee().getId() : null,
                r.getReviewee() != null ? r.getReviewee().getName() : null,
                r.getReviewer() != null ? r.getReviewer().getId() : null,
                r.getReviewer() != null ? r.getReviewer().getName() : null,
                r.getRating(),
                r.getComment(),
                r.getProject() != null ? r.getProject().getId() : null,
                r.getProject() != null ? r.getProject().getName() : null,
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
        );
    }
}