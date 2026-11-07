package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.FollowService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

 /** POST /follow/{followingId} — follow someone */
    @PostMapping("/follow/{followingId}")
    public Map<String, Object> follow(@PathVariable Long followingId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        Long followerId = AuthContext.id();
        return followService.follow(followerId, followingId);
    }

 /** POST /follow/{followingId}/unfollow */
    @PostMapping("/follow/{followingId}/unfollow")
    public Map<String, Object> unfollow(@PathVariable Long followingId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Long followerId = AuthContext.id();
        return followService.unfollow(followerId, followingId);
    }

 /** GET /follow/following — who I follow, */
    @GetMapping("/follow/following")
    public List<FollowService.PeerDto> following() {
        Long followerId = AuthContext.id();
        return followService.getFollowing(followerId);
    }

 /** GET /follow/followers — my followers, */
    @GetMapping("/follow/followers")
    public List<FollowService.PeerDto> followers() {
        Long followerId = AuthContext.id();
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

 /** GET /follow/stats — follower + following counts */
    @GetMapping("/follow/stats")
    public Map<String, Object> stats() {
        Long followerId = AuthContext.id();
        return followService.getStats(followerId);
    }

    /** GET /follow/stats/{studentId} — stats for any student */
    @GetMapping("/follow/stats/{studentId}")
    public Map<String, Object> statsOf(@PathVariable Long studentId) {
        return followService.getStats(studentId);
    }

    /** GET /follow/is-following/{followingId} */
    @GetMapping("/follow/is-following/{followingId}")
    public Map<String, Object> isFollowing(@PathVariable Long followingId) {
        Long followerId = AuthContext.id();
        boolean v = followService.isFollowing(followerId, followingId);
        return Map.of("followerId", followerId, "followingId", followingId, "following", v);
    }

}
