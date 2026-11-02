package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.FollowService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** POST /follow/{followingId} with X-Demo-User — follow someone */
    @PostMapping("/follow/{followingId}")
    public Map<String, Object> follow(@PathVariable Long followingId,
                                      @RequestHeader(value = "X-Demo-User", required = false) String header,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Long followerId = requireHeader(header, body);
        return followService.follow(followerId, followingId);
    }

    /** POST /follow/{followingId}/unfollow with X-Demo-User */
    @PostMapping("/follow/{followingId}/unfollow")
    public Map<String, Object> unfollow(@PathVariable Long followingId,
                                        @RequestHeader(value = "X-Demo-User", required = false) String header,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Long followerId = requireHeader(header, body);
        return followService.unfollow(followerId, followingId);
    }

    /** GET /follow/following — who I follow, with X-Demo-User */
    @GetMapping("/follow/following")
    public List<FollowService.PeerDto> following(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long followerId = requireHeader(header, null);
        return followService.getFollowing(followerId);
    }

    /** GET /follow/followers — my followers, with X-Demo-User */
    @GetMapping("/follow/followers")
    public List<FollowService.PeerDto> followers(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long followerId = requireHeader(header, null);
        return followService.getFollowers(followerId);
    }

    /** GET /follow/{studentId}/following — following for any student */
    @GetMapping("/follow/{studentId}/following")
    public List<FollowService.PeerDto> followingOf(@PathVariable Long studentId) {
        return followService.getFollowing(studentId);
    }

    /** GET /follow/{studentId}/followers — followers for any student */
    @GetMapping("/follow/{studentId}/followers")
    public List<FollowService.PeerDto> followersOf(@PathVariable Long studentId) {
        return followService.getFollowers(studentId);
    }

    /** GET /follow/stats with X-Demo-User — follower + following counts */
    @GetMapping("/follow/stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long followerId = requireHeader(header, null);
        return followService.getStats(followerId);
    }

    /** GET /follow/stats/{studentId} — stats for any student */
    @GetMapping("/follow/stats/{studentId}")
    public Map<String, Object> statsOf(@PathVariable Long studentId) {
        return followService.getStats(studentId);
    }

    /** GET /follow/is-following/{followingId} with X-Demo-User */
    @GetMapping("/follow/is-following/{followingId}")
    public Map<String, Object> isFollowing(@PathVariable Long followingId,
                                           @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long followerId = requireHeader(header, null);
        boolean v = followService.isFollowing(followerId, followingId);
        return Map.of("followerId", followerId, "followingId", followingId, "following", v);
    }

    private Long requireHeader(String header, Map<String, Object> body) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        if (body != null) {
            Object v = body.get("followerId");
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
