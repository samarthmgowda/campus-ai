package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.CommunityPost;
import com.campusconnect.campus_ai.service.CommunityService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * TEMPORARY - demo seed for community feed.
 */
@RestController
@RequestMapping("/dev")
public class DevSeedCommunityController {

    private final CommunityService communityService;

    public DevSeedCommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping("/seed-community")
    public Map<String, Object> seed() {
        List<Object[]> data = List.of(
                new Object[]{1L, "Just finished the Campus AI prototype — RAG + teammates + mentors in one chat. Thoughts?"},
                new Object[]{2L, "Looking for a backend partner for Smart Attendance IoT. DM if you know Spring Boot + hardware."},
                new Object[]{3L, "AWS Cloud Quest badge done — happy to mentor anyone starting cloud."},
                new Object[]{4L, "Published a Figma kit for Campus Events Portal. Free to fork!"},
                new Object[]{5L, "Mock interviews this weekend — 4 slots. Comment to book."},
                new Object[]{6L, "Built a lightweight vector search demo — pgvector is surprisingly fast for campus scale."}
        );

        int created = 0;
        List<Long> ids = new ArrayList<>();
        var existing = communityService.getFeed(200);
        Set<String> seen = new HashSet<>();
        for (var dto : existing) seen.add(dto.content());

        for (Object[] row : data) {
            Long authorId = (Long) row[0];
            String content = (String) row[1];
            if (seen.contains(content)) continue;
            try {
                CommunityPost p = communityService.createPost(authorId, content);
                ids.add(p.getId());
                created++;
            } catch (Exception e) {
                // skip if author missing
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("created", created);
        resp.put("ids", ids);
        return resp;
    }
}