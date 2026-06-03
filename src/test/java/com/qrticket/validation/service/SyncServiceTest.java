package com.qrticket.validation.service;

import com.qrticket.validation.dto.*;
import com.qrticket.validation.entity.CheckinSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private SyncService syncService;

    @Test
    @DisplayName("Пачка с миксом: OK, NOT_FOUND, ошибка — каждый обработан независимо")
    void mixedBatch() {
        CheckinRequest ok = makeRequest();
        CheckinRequest notFound = makeRequest();
        CheckinRequest broken = makeRequest();

        when(ticketService.checkin(eq(ok), eq(CheckinSource.OFFLINE_SYNC)))
                .thenReturn(TicketResponse.okToEnter("Иван", 42L));
        when(ticketService.checkin(eq(notFound), eq(CheckinSource.OFFLINE_SYNC)))
                .thenReturn(TicketResponse.notFound());
        when(ticketService.checkin(eq(broken), eq(CheckinSource.OFFLINE_SYNC)))
                .thenThrow(new RuntimeException("DB connection lost"));

        SyncRequest request = new SyncRequest(List.of(ok, notFound, broken));
        SyncResponse response = syncService.sync(request);

        assertEquals(3, response.results().size());

        // Первый — OK
        SyncResultItem r0 = response.results().get(0);
        assertEquals(ok.clientEventId(), r0.clientEventId());
        assertEquals(ValidationResult.OK_TO_ENTER, r0.result());
        assertNull(r0.errorMessage());

        // Второй — NOT_FOUND
        SyncResultItem r1 = response.results().get(1);
        assertEquals(ValidationResult.NOT_FOUND, r1.result());
        assertNull(r1.errorMessage());

        // Третий — ошибка, но пачка не упала
        SyncResultItem r2 = response.results().get(2);
        assertNull(r2.result());
        assertEquals("DB connection lost", r2.errorMessage());
    }

    @Test
    @DisplayName("Пустая пачка (граничный случай) — пустой результат")
    void emptyBatch() {
        SyncRequest request = new SyncRequest(List.of());
        SyncResponse response = syncService.sync(request);

        assertTrue(response.results().isEmpty());
    }

    private CheckinRequest makeRequest() {
        return new CheckinRequest(
                UUID.randomUUID(), 1L, UUID.randomUUID(), Instant.now());
    }
}