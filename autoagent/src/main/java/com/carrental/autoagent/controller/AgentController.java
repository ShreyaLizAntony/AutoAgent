package com.carrental.autoagent.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.carrental.autoagent.service.LlmService;

@RestController
@RequestMapping("/api/llama2")
public class AgentController {
    @Autowired
    private LlmService llmService;

    @PostMapping("/generate")
    public ResponseEntity<String> generate(@RequestBody String prompt) {
        String result = llmService.generateText(prompt);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}