package com.campusconnect.campus_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class OllamaChatService {

    private final RestClient restClient;
    private final String chatModel;

    public OllamaChatService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.chat-model}") String chatModel
    ) {
        // 120s timeout for local CPU generation
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(120_000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.chatModel = chatModel;
    }

    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", chatModel,
                "stream", false,
                "options", Map.of("temperature", 0.2),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        OllamaChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Could not reach Ollama or model not found (model=" + chatModel + ")", e);
        }

        if (response == null || response.message() == null || response.message().get("content") == null) {
            throw new IllegalStateException(
                    "Ollama returned empty or missing response (model=" + chatModel + ")");
        }

        return response.message().get("content").toString();
    }

    // Jackson maps nested JSON into these records
    private record OllamaChatResponse(Map<String, Object> message) {}
}