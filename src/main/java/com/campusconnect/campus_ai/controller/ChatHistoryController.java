package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.ChatHistoryService;
import com.campusconnect.campus_ai.service.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

 /** GET /ask/history?limit=20 */
    @GetMapping("/ask/history")
    public List<ChatHistoryService.ChatTurnDto> history(
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = AuthContext.id();
        return chatHistoryService.getHistory(userId, limit);
    }

 /** GET /ask/history/count */
    @GetMapping("/ask/history/count")
    public Map<String, Object> count() {
        Long userId = AuthContext.id();
        return Map.of("count", chatHistoryService.count(userId));
    }

 /** DELETE /ask/history */
    @DeleteMapping("/ask/history")
    public Map<String, Object> clear() {
        Long userId = AuthContext.id();
        long cleared = chatHistoryService.clear(userId);
        return Map.of("cleared", cleared);
    }

}
