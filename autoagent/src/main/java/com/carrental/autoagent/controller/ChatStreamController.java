package com.carrental.autoagent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.RequestMapping;

import com.carrental.autoagent.service.LlmService;

@RestController
@RequestMapping("/api")
public class ChatStreamController {
  private final LlmService llm;
  public ChatStreamController(LlmService llm){ this.llm = llm; }

  @GetMapping(path="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam String prompt) {
    return llm.stream(prompt);
  }
}