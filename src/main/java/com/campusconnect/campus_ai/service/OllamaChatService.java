package com.campusconnect.campus_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Ollama's /api/chat.
 *
 * Generation parameters are injected rather than hard-coded so the model and its
 * sampling can be A/B-tested from {@code application.properties} without a
 * recompile — the eval harness needs to compare arms under a changed model.
 *
 * The important one is {@code num_ctx}. Ollama's default context window is small
 * for many local models, and an over-long prompt is truncated silently: no error,
 * no warning, just a model that never saw the end of the retrieved passages and
 * answers from partial evidence. In a RAG prompt the numbered passages sit at the
 * *end* of the message, so the silently-lost tail is exactly the citations the
 * answer depends on. Setting it explicitly (and budgeting the prompt against it
 * in {@link AskService} via {@link ContextBudget}) makes the failure mode visible
 * instead of mysterious.
 */
@Service
public class OllamaChatService {

    private final RestClient restClient;
    private final String chatModel;
    private final int numCtx;
    private final int numPredict;
    private final double temperature;
    private final double topP;
    private final double repeatPenalty;

    public OllamaChatService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.chat-model}") String chatModel,
            @Value("${ollama.num-ctx:4096}") int numCtx,
            @Value("${ollama.num-predict:-1}") int numPredict,
            @Value("${ollama.temperature:0.2}") double temperature,
            @Value("${ollama.top-p:0.9}") double topP,
            @Value("${ollama.repeat-penalty:1.1}") double repeatPenalty
    ) {
        // 120s timeout for local CPU generation
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(120_000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.chatModel = chatModel;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.temperature = temperature;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
    }

    public String chat(String systemPrompt, String userPrompt) {
        // LinkedHashMap, not Map.of: the options need to stay readable as they grow
        // and -1 (Ollama's "generate until done") is a meaningful value, not a mistake.
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", temperature);
        options.put("top_p", topP);
        options.put("repeat_penalty", repeatPenalty);
        options.put("num_ctx", numCtx);
        options.put("num_predict", numPredict);

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "stream", false,
                "options", options,
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

    /** The context window this client advertises to Ollama, for prompt budgeting. */
    public int contextWindow() {
        return numCtx;
    }

    /** The active chat model, for diagnostics and the eval report. */
    public String model() {
        return chatModel;
    }

    // Jackson maps nested JSON into these records
    private record OllamaChatResponse(Map<String, Object> message) {}
}
