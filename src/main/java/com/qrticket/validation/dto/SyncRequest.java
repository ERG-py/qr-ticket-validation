package com.qrticket.validation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SyncRequest(
        @NotEmpty
        @Size(max = 1000)
        @Valid
        List<CheckinRequest> checkins
) {}