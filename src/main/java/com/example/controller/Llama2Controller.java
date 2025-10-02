package com.example.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.OllamaService;

@RestController
@RequestMapping("/api/llama2")
public class Llama2Controller {
    @Autowired
    private OllamaService ollamaService;

    @PostMapping("/generate")
    public ResponseEntity<String> generate(@RequestBody String prompt) {
        String result = ollamaService.generateText(prompt);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
