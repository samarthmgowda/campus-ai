package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ActivityService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

 /** GET /activity?limit=20&filter=ALL — unified timeline */
    @GetMapping("/activity")
    public List<Map<String, Object>> feed(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String filter) {
        Long userId = AuthContext.id();
        return activityService.getFeed(userId, limit, filter);
    }

}
