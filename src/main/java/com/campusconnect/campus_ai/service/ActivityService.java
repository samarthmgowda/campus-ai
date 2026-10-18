package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.repository.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final CommunityPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;
    private final ProjectInviteRepository inviteRepository;
    private final EventAttendanceRepository attendanceRepository;
    private final FollowRepository followRepository;
    private final BookmarkRepository bookmarkRepository;
    private final SkillEndorsementRepository endorsementRepository;
    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;

    public ActivityService(CommunityPostRepository postRepository,
                           PostLikeRepository likeRepository,
                           PostCommentRepository commentRepository,
                           ProjectInviteRepository inviteRepository,
                           EventAttendanceRepository attendanceRepository,
                           FollowRepository followRepository,
                           BookmarkRepository bookmarkRepository,
                           SkillEndorsementRepository endorsementRepository,
                           NotificationRepository notificationRepository,
                           StudentRepository studentRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.inviteRepository = inviteRepository;
        this.attendanceRepository = attendanceRepository;
        this.followRepository = followRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.endorsementRepository = endorsementRepository;
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
    }

    public List<Map<String, Object>> getFeed(Long userId, int limit, String filter) {
        studentRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        int lim = Math.max(1, Math.min(limit, 100));
        String filterNorm = filter != null ? filter.trim().toUpperCase() : "ALL";
        List<Map<String, Object>> items = new ArrayList<>();

        boolean wantAll = filterNorm.equals("ALL") || filterNorm.isEmpty();
        boolean wantPosts = wantAll || filterNorm.equals("POSTS") || filterNorm.equals("COMMUNITY");
        boolean wantInvites = wantAll || filterNorm.equals("INVITES") || filterNorm.equals("PROJECTS");
        boolean wantRsvps = wantAll || filterNorm.equals("RSVPS") || filterNorm.equals("EVENTS");
        boolean wantFollows = wantAll || filterNorm.equals("FOLLOWS") || filterNorm.equals("CONNECTIONS");
        boolean wantBookmarks = wantAll || filterNorm.equals("BOOKMARKS") || filterNorm.equals("SAVED");
        boolean wantEndorsements = wantAll || filterNorm.equals("ENDORSEMENTS") || filterNorm.equals("ENDORSE");
        boolean wantNotifications = wantAll || filterNorm.equals("NOTIFICATIONS") || filterNorm.equals("ALERTS");

        // posts: recent global feed (home) — last 10
        if (wantPosts) {
            try {
                var posts = postRepository.findAllByOrderByCreatedAtDesc();
                for (var p : posts.stream().limit(10).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "POST");
                    m.put("id", p.getId());
                    m.put("title", p.getAuthor() != null ? p.getAuthor().getName() + " posted" : "Post");
                    m.put("preview", preview(p.getContent(), 80));
                    m.put("at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
                    m.put("refType", "POST");
                    m.put("refId", p.getId());
                    m.put("likes", likeRepository.countByPostId(p.getId()));
                    m.put("comments", commentRepository.countByPostId(p.getId()));
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // invites sent + received for this user
        if (wantInvites) {
            try {
                var myInvites = inviteRepository.findByInviteeIdOrderByCreatedAtDesc(userId);
                var sentInvites = inviteRepository.findByInviterIdOrderByCreatedAtDesc(userId);
                for (var inv : myInvites.stream().limit(5).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "INVITE_RECEIVED");
                    m.put("id", inv.getId());
                    m.put("title", inv.getInviter().getName() + " → " + inv.getProject().getName() + " (" + inv.getStatus() + ")");
                    m.put("at", inv.getCreatedAt() != null ? inv.getCreatedAt().toString() : null);
                    m.put("refType", "PROJECT_INVITE");
                    m.put("refId", inv.getId());
                    m.put("status", inv.getStatus());
                    items.add(m);
                }
                for (var inv : sentInvites.stream().limit(5).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "INVITE_SENT");
                    m.put("id", inv.getId());
                    m.put("title", "You invited " + inv.getInvitee().getName() + " to " + inv.getProject().getName());
                    m.put("at", inv.getCreatedAt() != null ? inv.getCreatedAt().toString() : null);
                    m.put("refType", "PROJECT_INVITE");
                    m.put("refId", inv.getId());
                    m.put("status", inv.getStatus());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // rsvps
        if (wantRsvps) {
            try {
                var rsvps = attendanceRepository.findByStudentIdOrderByJoinedAtDesc(userId);
                for (var a : rsvps.stream().limit(5).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "RSVP");
                    m.put("id", a.getId());
                    m.put("title", "RSVP: " + a.getEvent().getTitle() + " (" + a.getEvent().getType() + ") on " + a.getEvent().getEventDate());
                    m.put("at", a.getJoinedAt() != null ? a.getJoinedAt().toString() : null);
                    m.put("refType", "EVENT");
                    m.put("refId", a.getEvent().getId());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // follows: give a short following/follower sample as activity (no per-event timestamp beyond follow createdAt)
        if (wantFollows) {
            try {
                var following = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId);
                var followers = followRepository.findByFollowingIdOrderByCreatedAtDesc(userId);
                for (var f : following.stream().limit(3).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "FOLLOWING");
                    m.put("id", f.getId());
                    m.put("title", "You follow " + f.getFollowing().getName());
                    m.put("at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
                    m.put("refType", "STUDENT");
                    m.put("refId", f.getFollowing().getId());
                    items.add(m);
                }
                for (var ff : followers.stream().limit(3).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "FOLLOWER");
                    m.put("id", ff.getId());
                    m.put("title", ff.getFollower().getName() + " follows you");
                    m.put("at", ff.getCreatedAt() != null ? ff.getCreatedAt().toString() : null);
                    m.put("refType", "STUDENT");
                    m.put("refId", ff.getFollower().getId());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // bookmarks
        if (wantBookmarks) {
            try {
                var bookmarks = bookmarkRepository.findByStudentIdOrderByCreatedAtDesc(userId);
                for (var b : bookmarks.stream().limit(5).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "BOOKMARK");
                    m.put("id", b.getId());
                    m.put("title", "Saved " + b.getRefType() + " " + b.getRefId());
                    m.put("at", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
                    m.put("refType", b.getRefType());
                    m.put("refId", b.getRefId());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // endorsements given/received
        if (wantEndorsements) {
            try {
                var received = endorsementRepository.findByEndorseeIdOrderByCreatedAtDesc(userId);
                var given = endorsementRepository.findByEndorserIdOrderByCreatedAtDesc(userId);
                for (var e : received.stream().limit(3).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "ENDORSEMENT_RECEIVED");
                    m.put("id", e.getId());
                    m.put("title", e.getEndorser().getName() + " endorsed your " + e.getSkill());
                    m.put("at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                    m.put("refType", "SKILL_ENDORSEMENT");
                    m.put("refId", e.getId());
                    items.add(m);
                }
                for (var e : given.stream().limit(2).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "ENDORSEMENT_GIVEN");
                    m.put("id", e.getId());
                    m.put("title", "You endorsed " + e.getEndorsee().getName() + " for " + e.getSkill());
                    m.put("at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                    m.put("refType", "SKILL_ENDORSEMENT");
                    m.put("refId", e.getId());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // recent notifications as a catch-all signal
        if (wantNotifications) {
            try {
                var notifs = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
                for (var n : notifs.stream().limit(5).toList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "NOTIFICATION");
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("preview", n.getBody());
                    m.put("at", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
                    m.put("refType", n.getRefType());
                    m.put("refId", n.getRefId());
                    m.put("readFlag", n.isReadFlag());
                    items.add(m);
                }
            } catch (Exception ignored) {}
        }

        // sort by at desc (ISO strings compare lexicographically), then limit
        items.sort((a, b) -> {
            String atA = (String) a.get("at");
            String atB = (String) b.get("at");
            if (atA == null && atB == null) return 0;
            if (atA == null) return 1;
            if (atB == null) return -1;
            return atB.compareTo(atA);
        });
        if (items.size() > lim) items = items.subList(0, lim);
        return items;
    }

    private String preview(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
