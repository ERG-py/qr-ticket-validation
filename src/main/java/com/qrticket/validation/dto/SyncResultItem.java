package com.qrticket.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncResultItem(
        UUID clientEventId,
        ValidationResult result,
        PreviousCheckinInfo previousCheckin,
        String errorMessage
) {
    public static SyncResultItem success(UUID clientEventId, TicketResponse response) {
        return new SyncResultItem(
                clientEventId,
                response.result(),
                response.previousCheckin(),
                null
        );
    }

    public static SyncResultItem error(UUID clientEventId, String message) {
        return new SyncResultItem(clientEventId, null, null, message);
    }
}