package com.qrticket.validation.controller;

import com.qrticket.validation.entity.Ticket;
import com.qrticket.validation.repository.TicketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final TicketRepository ticketRepository;

    public HealthController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

}