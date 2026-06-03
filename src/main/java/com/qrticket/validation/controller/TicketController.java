package com.qrticket.validation.controller;

import com.qrticket.validation.dto.CheckinRequest;
import com.qrticket.validation.dto.TicketResponse;
import com.qrticket.validation.dto.ValidateRequest;
import com.qrticket.validation.entity.CheckinSource;
import com.qrticket.validation.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/validate")
    public ResponseEntity<TicketResponse> validate(
            @Valid @RequestBody ValidateRequest request) {
        TicketResponse response = ticketService.validate(request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkin")
    public ResponseEntity<TicketResponse> checkin(
            @Valid @RequestBody CheckinRequest request) {
        TicketResponse response = ticketService.checkin(request, CheckinSource.ONLINE);
        return ResponseEntity.ok(response);
    }
}