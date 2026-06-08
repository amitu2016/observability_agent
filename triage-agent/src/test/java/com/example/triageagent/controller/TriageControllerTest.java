package com.example.triageagent.controller;

import com.example.triageagent.dto.TriageRequest;
import com.example.triageagent.service.TriageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TriageController.class)
@ActiveProfiles("test")
class TriageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TriageService triageService;

    @Test
    void controllerBeansAreLoaded() {
        assertNotNull(mockMvc, "MockMvc should be loaded");
        assertNotNull(triageService, "TriageService mock should be loaded");
    }

    @Test
    void investigate_returnsAnswerWith200() throws Exception {
        String expectedAnswer = "This is a mock triage response about the incident.";
        when(triageService.investigate(anyString())).thenReturn(expectedAnswer);

        TriageRequest request = new TriageRequest("Why is the service slow?");

        mockMvc.perform(post("/api/triage/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(expectedAnswer));
    }

    @Test
    void investigate_mapsRequestQuestionToResponse() throws Exception {
        String inputQuestion = "What caused the spike in error rate?";
        String mockResponse = "Based on the metrics, the error spike was caused by database connection pool exhaustion.";
        when(triageService.investigate(inputQuestion)).thenReturn(mockResponse);

        TriageRequest request = new TriageRequest(inputQuestion);

        mockMvc.perform(post("/api/triage/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.answer").value(mockResponse));
    }
}