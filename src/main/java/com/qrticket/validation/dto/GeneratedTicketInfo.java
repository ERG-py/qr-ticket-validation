package com.qrticket.validation.dto;

import java.util.UUID;

public record GeneratedTicketInfo(
        UUID code,
        String holderName
) {}