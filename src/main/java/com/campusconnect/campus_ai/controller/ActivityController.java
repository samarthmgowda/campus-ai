package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ActivityService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** GET /activity?limit=20&filter=ALL with X-Demo-User — unified timeline */
    @GetMapping("/activity")
    public List<Map<String, Object>> feed(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String filter) {
        Long userId = requireHeader(header);
        return activityService.getFeed(userId, limit, filter);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        try { return Long.valueOf(header.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid X-Demo-User"); }
    }
}
