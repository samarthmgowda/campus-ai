package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.CommunityPost;
import com.campusconnect.campus_ai.entity.PostComment;
import com.campusconnect.campus_ai.service.CommunityService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

 /** Create a post. POST /community {content} or body authorId */
    @PostMapping
    public CommunityService.PostDto create(
            @RequestBody Map<String, Object> body) {
        Long authorId = AuthContext.id();
        String content = body.get("content") instanceof String s ? s : "";
        CommunityPost p = communityService.createPost(authorId, content);
        return communityService.getPost(p.getId());
    }

    /** Feed — most recent posts. GET /community/feed?limit=20 */
    @GetMapping("/feed")
    public List<CommunityService.PostDto> feed(@RequestParam(defaultValue = "20") int limit) {
        return communityService.getFeed(limit);
    }

    /** Single post. GET /community/{id} */
    @GetMapping("/{id}")
    public CommunityService.PostDto getPost(@PathVariable Long id) {
        return communityService.getPost(id);
    }

    /** Posts by author. GET /community/author/{authorId} */
    @GetMapping("/author/{authorId}")
    public List<CommunityService.PostDto> byAuthor(@PathVariable Long authorId) {
        return communityService.getPostsByAuthor(authorId);
    }

 /** Toggle like. POST /community/{id}/like */
    @PostMapping("/{id}/like")
    public Map<String, Object> toggleLike(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = AuthContext.id();
        long count = communityService.toggleLike(id, userId);
        return Map.of("postId", id, "likeCount", count);
    }

 /** Add comment. POST /community/{id}/comment {content} */
    @PostMapping("/{id}/comment")
    public CommunityService.CommentDto addComment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.id();
        String content = body.get("content") instanceof String s ? s : "";
        PostComment c = communityService.addComment(id, userId, content);
        return new CommunityService.CommentDto(c.getId(), c.getPost().getId(),
                c.getAuthor() != null ? c.getAuthor().getId() : null,
                c.getAuthor() != null ? c.getAuthor().getName() : null,
                c.getContent(), c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
    }

    /** Comments for a post. GET /community/{id}/comments */
    @GetMapping("/{id}/comments")
    public List<CommunityService.CommentDto> getComments(@PathVariable Long id) {
        return communityService.getComments(id);
    }

}
