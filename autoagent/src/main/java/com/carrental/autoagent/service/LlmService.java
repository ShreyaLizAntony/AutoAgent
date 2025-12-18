package com.carrental.autoagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

@Service
public class LlmService {

    // ****** OPENROUTER CONFIG ******

    // Base URL for OpenRouter (OpenAI-compatible)
    private static final String OPENROUTER_BASE_URL =
            System.getenv().getOrDefault("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");

    // Full chat completions endpoint
    private static final String CHAT_COMPLETIONS_URL = OPENROUTER_BASE_URL + "/chat/completions";

    // Model to use
    private static final String OPENROUTER_MODEL =
            // System.getenv().getOrDefault("OPENROUTER_MODEL", "meta-llama/llama-3.2-3b-instruct");
            System.getenv().getOrDefault("OPENROUTER_MODEL", "tngtech/deepseek-r1t2-chimera:free");


    // API key (must be set in environment)
    private static final String OPENROUTER_API_KEY =
            System.getenv("OPENROUTER_API_KEY");

    // >>>>>> System prompt stays the same <<<<<<
    private static final String SYSTEM_PROMPT = """
        You are an intelligent assistant for a Car Rental Application.
        Answer questions as a car rental agent.
        - Answer user questions clearly and concisely.
        - Always stay within the context of car rental (vehicles, bookings, pricing, policies).
        - If asked something outside this domain, politely decline and redirect back to car rentals.
        - If the user’s request is ambiguous or incomplete, ask targeted clarifying questions.
        - If the answer is not present in the provided context, say you don’t know.
        """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // >>>>>> Inject RetrievalService here <<<<<< (in part 2)
    private final RetrievalService retrieval;

    public LlmService(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    // ---------- Non-streaming call using OpenRouter ----------

    public String generateText(String userPrompt) {
        try {
            if (OPENROUTER_API_KEY == null || OPENROUTER_API_KEY.isBlank()) {
                throw new IllegalStateException("OPENROUTER_API_KEY is not set in environment variables");
            }

            // RAG: pull chunks
            List<String> chunks = retrieval.query(userPrompt, 6).stream().distinct().toList();
            System.out.println("[RAG] Using " + chunks.size() + " chunk(s) for: " + userPrompt);

            String context = String.join("\n---\n", chunks);
            String userContent = chunks.isEmpty()
                    ? userPrompt
                    : """
                      Use ONLY the context below to answer. If unsure, say you don't know.
                      Context:
                      ---
                      %s
                      ---
                      Question: %s
                      """.formatted(context, userPrompt);

            // Build OpenRouter (OpenAI-style) JSON payload
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", OPENROUTER_MODEL);
            payload.put("temperature", 0.2);
            // not streaming here
            payload.put("stream", false);

            ArrayNode messages = payload.putArray("messages");
            messages.add(mapper.createObjectNode()
                    .put("role", "system")
                    .put("content", SYSTEM_PROMPT));
            messages.add(mapper.createObjectNode()
                    .put("role", "user")
                    .put("content", userContent));

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_COMPLETIONS_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + OPENROUTER_API_KEY)
                    // Recommended by OpenRouter (identify your app)
                    .header("HTTP-Referer", "https://github.com/ShreyaLizAntony/CarRentalAgent")
                    .header("X-Title", "Car Rental Agent")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("OpenRouter error: " + response.statusCode() + " body: " + response.body());
                return "Error: OpenRouter returned " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "(no response)";
            }

            String reply = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText("(no response)");

            return reply;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    // ---------- Streaming call using OpenRouter (SSE) ----------

    public SseEmitter stream(String prompt) {
        System.out.println(">> OpenRouterService.stream prompt=" + prompt);
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                if (OPENROUTER_API_KEY == null || OPENROUTER_API_KEY.isBlank()) {
                    throw new IllegalStateException("OPENROUTER_API_KEY is not set in environment variables");
                }

                // Let client know the stream is live
                emitter.send(SseEmitter.event().data("[ready]"));

                // ---------- RAG context ----------
                List<String> chunks = retrieval.query(prompt, 6).stream().distinct().toList();
                System.out.println("[RAG] Using " + chunks.size() + " chunk(s) for: " + prompt);

                String context = String.join("\n---\n", chunks);
                String userContent = chunks.isEmpty()
                        ? prompt
                        : """
                        Use ONLY the context below to answer. If unsure, say you don't know.
                        Context:
                        ---
                        %s
                        ---
                        Question: %s
                        """.formatted(context, prompt);

                // ---------- Build streaming request payload ----------
                ObjectNode payload = mapper.createObjectNode();
                payload.put("model", OPENROUTER_MODEL);
                payload.put("temperature", 0.2);
                payload.put("stream", true);

                ArrayNode messages = payload.putArray("messages");
                messages.add(mapper.createObjectNode()
                        .put("role", "system")
                        .put("content", SYSTEM_PROMPT));
                messages.add(mapper.createObjectNode()
                        .put("role", "user")
                        .put("content", userContent));

                String jsonBody = mapper.writeValueAsString(payload);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(CHAT_COMPLETIONS_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + OPENROUTER_API_KEY)
                        .header("HTTP-Referer", "https://github.com/ShreyaLizAntony/AutoAgent")
                        .header("X-Title", "CarRentalAgent")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<InputStream> resp =
                        httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    String errorBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                    System.err.println("OpenRouter stream error: " + resp.statusCode() + " body: " + errorBody);
                    emitter.send(SseEmitter.event().name("error")
                            .data("OpenRouter error: " + resp.statusCode()));
                    emitter.complete();
                    return;
                }

                // ---------- Read SSE stream ----------
                try (BufferedReader br =
                            new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {

                    String line;
                    StringBuilder buffer = new StringBuilder();
                    final int FLUSH_THRESHOLD = 40; // chars before we send to client

                    while ((line = br.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }

                        // Expect lines like "data: {...}" or "data: [DONE]"
                        if (!line.startsWith("data:")) {
                            continue;
                        }

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        JsonNode node = mapper.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (!choices.isArray() || choices.isEmpty()) {
                            continue;
                        }

                        JsonNode delta = choices.get(0).path("delta");
                        String piece = delta.path("content").asText("");

                        if (piece.isEmpty()) {
                            continue;
                        }

                        // Accumulate pieces so the frontend gets bigger chunks (better formatting)
                        buffer.append(piece);

                        if (buffer.length() >= FLUSH_THRESHOLD || piece.contains("\n")) {
                            emitter.send(SseEmitter.event().data(buffer.toString()));
                            buffer.setLength(0);
                        }
                    }

                    // Flush any remaining text
                    if (buffer.length() > 0) {
                        emitter.send(SseEmitter.event().data(buffer.toString()));
                    }
                }

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
                } catch (Exception ignore) {}
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}