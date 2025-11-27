package com.carrental.autoagent.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class DocumentIngestionService {

    private final RetrievalService retrievalService;

    public DocumentIngestionService(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostConstruct
    public void ingestOnStartup() {
        try {
            // Folder inside src/main/resources
            Path docsDir = new ClassPathResource("docs").getFile().toPath();

            try (Stream<Path> paths = Files.walk(docsDir)) {
                paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".txt"))
                     .forEach(this::ingestTxtFile);
            }

            System.out.println("[RAG] Ingestion complete.");
        } catch (Exception e) {
            System.err.println("[RAG] Ingestion failed: " + e.getMessage());
        }
    }

    private void ingestTxtFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String chunk : chunk(content, 800, 200)) { // 800 chars with 200 overlap
                retrievalService.insert(chunk);
            }
            System.out.println("[RAG] Indexed: " + file.getFileName());
        } catch (Exception e) {
            System.err.println("[RAG] Failed to index " + file + ": " + e.getMessage());
        }
    }

    /** Simple character-based chunker with overlap */
    private static Iterable<String> chunk(String text, int size, int overlap) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int i = 0, n = text.length();
        while (i < n) {
            int end = Math.min(n, i + size);
            chunks.add(text.substring(i, end).trim());
            if (end == n) break;
            i = Math.max(end - overlap, i + 1);
        }
        return chunks;
    }
}
