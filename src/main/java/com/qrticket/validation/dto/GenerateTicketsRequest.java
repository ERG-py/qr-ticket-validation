package com.qrticket.validation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateTicketsRequest(
        @Min(1)
        @Max(100)
        int count
) {}