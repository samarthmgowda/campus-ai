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

 /**
  * GET /ask/history?limit=20
  *
  * When {@code conversationId} is supplied, returns that thread oldest-first;
  * otherwise returns the most recent turns across all threads (newest-first),
  * preserving the pre-thread behaviour.
  */
    @GetMapping("/ask/history")
    public List<ChatHistoryService.ChatTurnDto> history(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String conversationId) {
        Long userId = AuthContext.id();
        if (conversationId != null) {
            return chatHistoryService.getHistory(userId, conversationId, limit);
        }
        return chatHistoryService.getHistory(userId, limit);
    }

 /**
  * GET /ask/conversations — the caller's threads, newest first.
  */
    @GetMapping("/ask/conversations")
    public List<ChatHistoryService.ConversationDto> conversations() {
        Long userId = AuthContext.id();
        return chatHistoryService.listConversations(userId);
    }

 /**
  * GET /ask/history/count
  */
    @GetMapping("/ask/history/count")
    public Map<String, Object> count() {
        Long userId = AuthContext.id();
        return Map.of("count", chatHistoryService.count(userId));
    }

 /**
  * DELETE /ask/history?conversationId= — clears one thread, or all turns when
  * no conversationId is supplied.
  */
    @DeleteMapping("/ask/history")
    public Map<String, Object> clear(@RequestParam(required = false) String conversationId) {
        Long userId = AuthContext.id();
        if (conversationId != null) {
            return Map.of("cleared", chatHistoryService.clearConversation(userId, conversationId));
        }
        return Map.of("cleared", chatHistoryService.clear(userId));
    }

}
