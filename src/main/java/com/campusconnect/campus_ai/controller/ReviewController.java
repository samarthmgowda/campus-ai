package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Review;
import com.campusconnect.campus_ai.service.ReviewService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Student writes a review for another student.
 * Reviewer identity comes from the JWT principal (never the body).
     * POST /reviews {revieweeId, rating (1–5), comment, projectId (optional)}
     */
    @PostMapping
    public ReviewService.ReviewDto write(
            @RequestBody Map<String, Object> body) {
        Long reviewerId = AuthContext.id();
        Long revieweeId = getLong(body, "revieweeId");
        if (revieweeId == null) throw new IllegalArgumentException("revieweeId required");
        Integer rating = getInt(body, "rating");
        String comment = body.get("comment") instanceof String s ? s : null;
        Long projectId = getLong(body, "projectId");

        Review r = reviewService.writeReview(revieweeId, reviewerId, rating, comment, projectId);
        return reviewService.getReviewsForStudent(revieweeId).stream()
                .filter(d -> d.id().equals(r.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Reviews of a student + reputation.
     * GET /reviews/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public Map<String, Object> getForStudent(@PathVariable Long studentId) {
        List<ReviewService.ReviewDto> reviews = reviewService.getReviewsForStudent(studentId);
        Map<String, Object> reputation = reviewService.getReputationForStudent(studentId);
        return Map.of("reviews", reviews, "reputation", reputation);
    }

    /**
     * Reputation only.
     * GET /reviews/reputation/{studentId} → {studentId, reviewCount, averageRating}
     */
    @GetMapping("/reputation/{studentId}")
    public Map<String, Object> getReputation(@PathVariable Long studentId) {
        return reviewService.getReputationForStudent(studentId);
    }

    /**
     * Reviews scoped to a project.
     * GET /reviews/project/{projectId}
     */
    @GetMapping("/project/{projectId}")
    public List<ReviewService.ReviewDto> getForProject(@PathVariable Long projectId) {
        return reviewService.getReviewsForProject(projectId);
    }

    private Long getLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) return Long.valueOf(s.trim());
        return null;
    }

    private Integer getInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) return Integer.valueOf(s.trim());
        return null;
    }
}