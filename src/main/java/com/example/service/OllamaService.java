package com.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OllamaService {
    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private final ObjectMapper mapper = new ObjectMapper();

    // 🔹 RAG: inject your retrieval client
    @Autowired
    private RetrievalService retrievalService;

    // You can tune this
    private static final int TOP_K = 3;

    public String generateText(String userPrompt) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(OLLAMA_URL);

            // 1) Retrieve top-K chunks for this query
            List<String> topChunks = retrievalService.query(userPrompt, TOP_K);
            String context = String.join("\n\n", topChunks);

            // 2) System prompt + RAG context
            String systemPrompt = """
                You are an intelligent assistant for a Car Rental Application.
                Answer questions as a car rental agent.
                - Answer user questions clearly and concisely.
                - Always stay within the context of car rental (vehicles, bookings, pricing, policies).
                - If asked something outside this domain, politely decline and redirect back to car rentals.
                - If the user’s request is ambiguous or incomplete, ask targeted clarifying questions.
                - If the answer is not present in the provided context, say you don’t know.

                ===== CONTEXT (retrieved) =====
                """ + (context.isBlank() ? "(no relevant context retrieved)" : context) + """
                \n===== END CONTEXT =====
                """;

            // 3) Build /api/chat payload (non-streaming)
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", "llama2");       // e.g. "llama2:13b-chat" if you have that
            payload.put("stream", false);

            ArrayNode messages = mapper.createArrayNode();
            messages.add(mapper.createObjectNode()
                    .put("role", "system")
                    .put("content", systemPrompt));
            messages.add(mapper.createObjectNode()
                    .put("role", "user")
                    .put("content", userPrompt));
            payload.set("messages", messages);

            ObjectNode options = mapper.createObjectNode();
            options.put("temperature", 0.2);
            options.put("top_p", 0.9);
            options.put("num_predict", 512);
            // (Optional) enlarge context window if your model build supports it:
            // options.put("num_ctx", 4096);
            payload.set("options", options);

            post.setEntity(new StringEntity(mapper.writeValueAsString(payload), ContentType.APPLICATION_JSON));

            // 4) Execute and parse: message.content
            try (CloseableHttpResponse resp = client.execute(post)) {
                String body = EntityUtils.toString(resp.getEntity());
                JsonNode root = mapper.readTree(body);
                return root.path("message").path("content").asText("(no response)");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}



// package com.example.service;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.node.ObjectNode;
// import com.fasterxml.jackson.databind.node.ArrayNode;
// import org.apache.hc.client5.http.classic.methods.HttpPost;
// import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
// import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
// import org.apache.hc.client5.http.impl.classic.HttpClients;
// import org.apache.hc.core5.http.io.entity.StringEntity;
// import org.apache.hc.core5.http.ClassicHttpResponse;
// import org.springframework.stereotype.Service;
// import org.apache.hc.core5.http.ContentType;
// import org.apache.hc.core5.http.io.entity.EntityUtils;

// import java.io.BufferedReader;
// import java.io.InputStreamReader;

// @Service
// public class OllamaService {
//     private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
//     private final ObjectMapper mapper = new ObjectMapper();

//     public String generateText(String prompt) {
//         try (CloseableHttpClient client = HttpClients.createDefault()) {
//             HttpPost post = new HttpPost(OLLAMA_URL);

//             // --- System Prompt ---
//             String systemPrompt = """
//             You are an intelligent assistant for a Car Rental Application.
//             Answer questions as a car rental agent.
//             - Answer user questions clearly and concisely.
//             - Always stay within the context of car rental (vehicles, bookings, pricing, policies).
//             - If asked something outside this domain, politely decline and redirect back to car rentals.
//             - If the user’s request is ambiguous or incomplete, politely ask clarifying questions instead of guessing.
//             """;

//             // --- Build JSON payload using Jackson ---
//             ObjectNode payload = mapper.createObjectNode();
//             payload.put("model", "llama2");  // or "llama2:13b-chat" if that's what you pulled
//             payload.put("stream", false);

//             // Messages array
//             ArrayNode messages = mapper.createArrayNode();
//             messages.add(mapper.createObjectNode()
//                 .put("role", "system")
//                 .put("content", systemPrompt));
//             messages.add(mapper.createObjectNode()
//                 .put("role", "user")
//                 .put("content", prompt));
//             payload.set("messages", messages);

//             // Options
//             ObjectNode options = mapper.createObjectNode();
//             options.put("temperature", 0.2);
//             options.put("top_p", 0.9);
//             options.put("num_predict", 512);
//             payload.set("options", options);

//             // Attach JSON body
//             String json = mapper.writeValueAsString(payload);
//             post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

//             // --- Execute and parse response ---
//             try (CloseableHttpResponse resp = client.execute(post)) {
//                 String body = EntityUtils.toString(resp.getEntity());
//                 JsonNode root = mapper.readTree(body);
//                 return root.path("message").path("content").asText("(no response)");
//             }

//         } catch (Exception e) {
//             e.printStackTrace();
//             return "Error: " + e.getMessage();
//         }
//     }
// }