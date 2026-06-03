package com.qrticket.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketResponse(
        ValidationResult result,
        String holderName,
        Long eventId,
        PreviousCheckinInfo previousCheckin
) {
    public static TicketResponse notFound() {
        return new TicketResponse(ValidationResult.NOT_FOUND, null, null, null);
    }

    public static TicketResponse invalid() {
        return new TicketResponse(ValidationResult.INVALID, null, null, null);
    }

    public static TicketResponse okToEnter(String holderName, Long eventId) {
        return new TicketResponse(ValidationResult.OK_TO_ENTER, holderName, eventId, null);
    }

    public static TicketResponse alreadyUsed(String holderName, Long eventId, PreviousCheckinInfo previous) {
        return new TicketResponse(ValidationResult.ALREADY_USED, holderName, eventId, previous);
    }
}