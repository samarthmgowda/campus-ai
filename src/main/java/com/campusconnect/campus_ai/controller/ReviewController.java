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
        Long revieweeId = null;
        if (body != null) {
            Object revieweeIdObj = body.get("revieweeId");
            if (revieweeIdObj instanceof Number) {
                revieweeId = ((Number) revieweeIdObj).longValue();
            } else if (revieweeIdObj instanceof String) {
                try {
                    revieweeId = Long.valueOf((String) revieweeIdObj);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (revieweeId == null) throw new IllegalArgumentException("revieweeId required");
        Integer rating = null;
        Object ratingObj = body.get("rating");
        if (ratingObj instanceof Number) {
            rating = ((Number) ratingObj).intValue();
        }
        String comment = null;
        Object commentObj = body.get("comment");
        if (commentObj instanceof String) {
            comment = (String) commentObj;
        }
        Long projectId = null;
        Object projectIdObj = body.get("projectId");
        if (projectIdObj instanceof Number) {
            projectId = ((Number) projectIdObj).longValue();
        } else if (projectIdObj instanceof String) {
            try {
                projectId = Long.valueOf((String) projectIdObj);
            } catch (NumberFormatException ignored) {}
        }

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

}