package com.qrticket.validation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ValidateRequest(
        @NotNull UUID code
) {}