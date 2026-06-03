package com.qrticket.validation.dto;

import java.time.Instant;

public record PreviousCheckinInfo(
        Instant checkedAt,
        Long gateId
) {}