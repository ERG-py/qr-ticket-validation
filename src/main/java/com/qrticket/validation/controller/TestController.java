package com.qrticket.validation.controller;

import com.qrticket.validation.dto.GenerateTicketsRequest;
import com.qrticket.validation.dto.GenerateTicketsResponse;
import com.qrticket.validation.service.TestDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Profile("dev")
public class TestController {

    private final TestDataService testDataService;

    @PostMapping("/tickets")
    public ResponseEntity<GenerateTicketsResponse> generateTickets(
            @Valid @RequestBody GenerateTicketsRequest request) {
        GenerateTicketsResponse response = testDataService.generateTickets(request.count());
        return ResponseEntity.ok(response);
    }
}