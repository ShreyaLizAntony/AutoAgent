package com.carrental.ollama_agent.service;

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
public class OllamaService {

    // ****** YOU CAN REPLACE THIS URL WITH ANY OTHER LLM API ******
    //**** Please read the documentation for the model/API you wish to replace this with for any additional properties/authentication that may be required****
    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    //*******************************************************************

    // >>>>>> Add System prompt here <<<<<<
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

    // >>>>>> Inject RetrievalService here <<<<<< (in part 2)
    private final RetrievalService retrieval;

    public OllamaService(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    public String generateText(String userPrompt) {
        try {
            // >>>>> Pull chunks and generate userContent here <<<<<< (in part 2)
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

            ObjectNode payload = mapper.createObjectNode();
            //*****You may replace this with any other ollama model you have installed locally *****
            payload.put("model", "llama3.1:8b-instruct-q4_K_M");
            payload.put("stream", false);
            
            ObjectNode options = mapper.createObjectNode();
            options.put("temperature", 0.2);
            options.put("num_ctx", 8192);
            payload.set("options", options);

            ArrayNode messages = mapper.createArrayNode();

            // >>>>>> Add System message here <<<<<<
            messages.add(mapper.createObjectNode().put("role", "system").put("content", SYSTEM_PROMPT));

            // >>>>> userPrompt here is to be updated to userContent in part 2 <<<<<
            messages.add(mapper.createObjectNode().put("role", "user").put("content", userContent));

            payload.set("messages", messages);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            return root.path("message").path("content").asText("(no response)");
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public SseEmitter stream(String prompt) {
        System.out.println(">> OllamaService.stream prompt=" + prompt);
        SseEmitter emitter = new SseEmitter(0L); 

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // Immediate heartbeat so clients know the stream is live
                emitter.send(SseEmitter.event().data("[ready]"));

                //<<<<< Add Rag Context here <<<<<< (in part 2)
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

                // Build streaming payload
                ObjectNode payload = mapper.createObjectNode();
                payload.put("model", "llama3.1:8b-instruct-q4_K_M");
                payload.put("stream", true);
                ObjectNode options = mapper.createObjectNode();
                options.put("temperature", 0.2);
                options.put("num_ctx", 8192);
                payload.set("options", options);

                ArrayNode messages = mapper.createArrayNode();
                messages.add(mapper.createObjectNode().put("role", "system").put("content", SYSTEM_PROMPT));
                messages.add(mapper.createObjectNode().put("role", "user").put("content", userContent));
                payload.set("messages", messages);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (BufferedReader br =
                             new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isBlank()) continue;
                        JsonNode node = mapper.readTree(line);
                        String piece = node.path("message").path("content").asText();
                        if (!piece.isEmpty()) {
                            emitter.send(SseEmitter.event().data(piece));
                        }
                        if (node.path("done").asBoolean(false)) {
                            break;
                        }
                    }
                }

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception ex) {
                try { emitter.send(SseEmitter.event().name("error").data(ex.getMessage())); } catch (Exception ignore) {}
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}