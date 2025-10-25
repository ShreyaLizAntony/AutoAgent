package com.carrental.ollama_agent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.RequestMapping;

import com.carrental.ollama_agent.service.OllamaService;

@RestController
@RequestMapping("/api")
public class ChatStreamController {
  private final OllamaService ollama;
  public ChatStreamController(OllamaService ollama){ this.ollama = ollama; }

  @GetMapping(path="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam String prompt) {
    return ollama.stream(prompt);
  }
}