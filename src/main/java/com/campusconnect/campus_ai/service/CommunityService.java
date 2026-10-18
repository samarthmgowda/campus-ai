package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.CommunityPost;
import com.campusconnect.campus_ai.entity.PostComment;
import com.campusconnect.campus_ai.entity.PostLike;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.CommunityPostRepository;
import com.campusconnect.campus_ai.repository.PostCommentRepository;
import com.campusconnect.campus_ai.repository.PostLikeRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    private final CommunityPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;

    public CommunityService(CommunityPostRepository postRepository,
                             PostLikeRepository likeRepository,
                             PostCommentRepository commentRepository,
                             StudentRepository studentRepository,
                             NotificationService notificationService) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
    }

    public CommunityPost createPost(Long authorId, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Post content must not be blank");
        Student author = studentRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Author not found: " + authorId));
        CommunityPost p = new CommunityPost(author, content.trim());
        return postRepository.save(p);
    }

    public List<PostDto> getFeed(int limit) {
        List<CommunityPost> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> !post.isHidden()).collect(Collectors.toList());
        if (limit > 0 && posts.size() > limit) posts = posts.subList(0, limit);
        return posts.stream().map(this::toDto).collect(Collectors.toList());
    }

    public PostDto getPost(Long postId) {
        CommunityPost p = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        if (p.isHidden()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Post hidden by moderation");
        return toDto(p);
    }

    /** Toggle like: if already liked, remove; otherwise add. Returns new likeCount. */
    public long toggleLike(Long postId, Long studentId) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Student actor = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        var existing = likeRepository.findByPostIdAndStudentId(postId, studentId);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
        } else {
            likeRepository.save(new PostLike(post, actor));
            // notify post owner (skip self-like)
            try {
                if (post.getAuthor() != null) {
                    String preview = post.getContent() != null && post.getContent().length() > 60
                            ? post.getContent().substring(0, 57) + "..."
                            : post.getContent();
                    notificationService.notifyOther(post.getAuthor().getId(), studentId,
                            "LIKE", actor.getName() + " liked your post",
                            "\"" + preview + "\"", "POST", post.getId());
                }
            } catch (Exception ignored) {}
        }
        return likeRepository.countByPostId(postId);
    }

    public PostComment addComment(Long postId, Long authorId, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Comment must not be blank");
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        Student author = studentRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Author not found: " + authorId));
        PostComment c = new PostComment(post, author, content.trim());
        c = commentRepository.save(c);
        try {
            if (post.getAuthor() != null) {
                String preview = c.getContent().length() > 80 ? c.getContent().substring(0, 77) + "..." : c.getContent();
                notificationService.notifyOther(post.getAuthor().getId(), authorId,
                        "COMMENT", author.getName() + " commented on your post",
                        "\"" + preview + "\"", "POST", post.getId());
            }
        } catch (Exception ignored) {}
        return c;
    }

    public List<CommentDto> getComments(Long postId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(c -> !c.isHidden())
                .map(c -> new CommentDto(c.getId(), c.getPost().getId(),
                        c.getAuthor() != null ? c.getAuthor().getId() : null,
                        c.getAuthor() != null ? c.getAuthor().getName() : null,
                        c.getContent(), c.getCreatedAt() != null ? c.getCreatedAt().toString() : null))
                .collect(Collectors.toList());
    }

    public List<PostDto> getPostsByAuthor(Long authorId) {
        return postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    // ——— DTOs ———

    public record PostDto(Long id, Long authorId, String authorName, String content, String createdAt, long likeCount, long commentCount) {}
    public record CommentDto(Long id, Long postId, Long authorId, String authorName, String content, String createdAt) {}

    private PostDto toDto(CommunityPost p) {
        long likes = likeRepository.countByPostId(p.getId());
        long comments = commentRepository.countByPostId(p.getId());
        return new PostDto(p.getId(),
                p.getAuthor() != null ? p.getAuthor().getId() : null,
                p.getAuthor() != null ? p.getAuthor().getName() : null,
                p.getContent(),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
                likes, comments);
    }
}
