package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ChatHistoryService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    /** GET /ask/history?limit=20 with X-Demo-User */
    @GetMapping("/ask/history")
    public List<ChatHistoryService.ChatTurnDto> history(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = requireHeader(header);
        return chatHistoryService.getHistory(userId, limit);
    }

    /** GET /ask/history/count with X-Demo-User */
    @GetMapping("/ask/history/count")
    public Map<String, Object> count(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        return Map.of("count", chatHistoryService.count(userId));
    }

    /** DELETE /ask/history with X-Demo-User */
    @DeleteMapping("/ask/history")
    public Map<String, Object> clear(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        long cleared = chatHistoryService.clear(userId);
        return Map.of("cleared", cleared);
    }

    private Long requireHeader(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X-Demo-User header required");
        return DemoIdentity.requireId(header);
    }
}
