package com.qrticket.validation.dto;

import java.util.List;

public record GenerateTicketsResponse(
        List<GeneratedTicketInfo> tickets,
        String qrDirectory
) {}