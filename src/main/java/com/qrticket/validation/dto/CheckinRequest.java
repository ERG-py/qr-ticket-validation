package com.qrticket.validation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CheckinRequest(
        @NotNull UUID code,
        @NotNull Long gateId,
        @NotNull UUID clientEventId,
        @NotNull Instant checkedAt
) {}