package com.qrticket.validation.dto;

import java.time.Instant;

public record ErrorResponse(
        String error,
        int status,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error) {
        return new ErrorResponse(error, status, Instant.now());
    }
}