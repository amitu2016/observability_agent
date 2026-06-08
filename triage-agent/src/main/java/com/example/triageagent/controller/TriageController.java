package com.example.triageagent.controller;

import com.example.triageagent.dto.TriageRequest;
import com.example.triageagent.dto.TriageResponse;
import com.example.triageagent.service.TriageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping("/investigate")
    public ResponseEntity<TriageResponse> investigate(@RequestBody TriageRequest request) {
        String answer = triageService.investigate(request.getQuestion());
        return ResponseEntity.ok(new TriageResponse(answer));
    }
}