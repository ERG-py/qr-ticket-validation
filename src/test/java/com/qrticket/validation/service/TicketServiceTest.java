package com.qrticket.validation.service;

import com.qrticket.validation.dto.CheckinRequest;
import com.qrticket.validation.dto.TicketResponse;
import com.qrticket.validation.dto.ValidationResult;
import com.qrticket.validation.entity.Checkin;
import com.qrticket.validation.entity.CheckinSource;
import com.qrticket.validation.entity.Gate;
import com.qrticket.validation.entity.Ticket;
import com.qrticket.validation.entity.TicketStatus;
import com.qrticket.validation.repository.CheckinRepository;
import com.qrticket.validation.repository.GateRepository;
import com.qrticket.validation.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckinRepository checkinRepository;

    @Mock
    private GateRepository gateRepository;

    @InjectMocks
    private TicketService ticketService;

    private UUID validCode;
    private Ticket validTicket;
    private Gate gate;

    @BeforeEach
    void setUp() {
        validCode = UUID.randomUUID();

        validTicket = new Ticket();
        validTicket.setId(1L);
        validTicket.setCode(validCode);
        validTicket.setStatus(TicketStatus.VALID);
        validTicket.setHolderName("Иван Иванов");
        validTicket.setEventId(42L);
        validTicket.setValidFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        validTicket.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59));

        gate = new Gate();
        gate.setId(1L);
        gate.setName("Главный вход");
    }

    // ========== validate() ==========

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("Билет не найден → NOT_FOUND")
        void notFound() {
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.empty());

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.NOT_FOUND, response.result());
            assertNull(response.holderName());
        }

        @Test
        @DisplayName("Билет отменён → INVALID")
        void cancelled() {
            validTicket.setStatus(TicketStatus.CANCELLED);
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.INVALID, response.result());
        }

        @Test
        @DisplayName("Билет ещё не начал действовать → INVALID")
        void beforeValidFrom() {
            validTicket.setValidFrom(LocalDateTime.now(ZoneOffset.UTC).plusDays(10));
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.INVALID, response.result());
        }

        @Test
        @DisplayName("Билет просрочен → INVALID")
        void afterValidTo() {
            validTicket.setValidTo(LocalDateTime.now(ZoneOffset.UTC).minusDays(10));
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.INVALID, response.result());
        }

        @Test
        @DisplayName("Уже был проход → ALREADY_USED с previousCheckin")
        void alreadyUsed() {
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));

            Checkin existing = new Checkin();
            existing.setCheckedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
            existing.setGate(gate);
            when(checkinRepository.findByTicketId(1L)).thenReturn(Optional.of(existing));

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.ALREADY_USED, response.result());
            assertEquals("Иван Иванов", response.holderName());
            assertNotNull(response.previousCheckin());
            assertEquals(1L, response.previousCheckin().gateId());
        }

        @Test
        @DisplayName("Всё чисто → OK_TO_ENTER")
        void okToEnter() {
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));
            when(checkinRepository.findByTicketId(1L)).thenReturn(Optional.empty());

            TicketResponse response = ticketService.validate(validCode);

            assertEquals(ValidationResult.OK_TO_ENTER, response.result());
            assertEquals("Иван Иванов", response.holderName());
            assertEquals(42L, response.eventId());
            assertNull(response.previousCheckin());
        }
    }

    // ========== checkin() ==========

    @Nested
    @DisplayName("checkin()")
    class CheckinTests {

        private CheckinRequest request;

        @BeforeEach
        void setUp() {
            request = new CheckinRequest(
                    validCode, 1L, UUID.randomUUID(), Instant.now());
        }

        @Test
        @DisplayName("Идемпотентность: повторный clientEventId → ALREADY_USED")
        void idempotent() {
            Checkin duplicate = new Checkin();
            duplicate.setCheckedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
            duplicate.setGate(gate);
            duplicate.setTicket(validTicket);
            when(checkinRepository.findByClientEventId(request.clientEventId()))
                    .thenReturn(Optional.of(duplicate));

            TicketResponse response = ticketService.checkin(request, CheckinSource.ONLINE);

            assertEquals(ValidationResult.ALREADY_USED, response.result());
            // Не должен был лезть в ticketRepository — вышел на первой проверке
            verify(ticketRepository, never()).findByCode(any());
        }

        @Test
        @DisplayName("Билет не найден → NOT_FOUND")
        void notFound() {
            when(checkinRepository.findByClientEventId(any())).thenReturn(Optional.empty());
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.empty());

            TicketResponse response = ticketService.checkin(request, CheckinSource.ONLINE);

            assertEquals(ValidationResult.NOT_FOUND, response.result());
        }

        @Test
        @DisplayName("Успешный checkin → OK_TO_ENTER, билет переведён в USED")
        void success() {
            when(checkinRepository.findByClientEventId(any())).thenReturn(Optional.empty());
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));
            when(checkinRepository.findByTicketId(1L)).thenReturn(Optional.empty());
            when(gateRepository.findById(1L)).thenReturn(Optional.of(gate));
            when(checkinRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TicketResponse response = ticketService.checkin(request, CheckinSource.ONLINE);

            assertEquals(ValidationResult.OK_TO_ENTER, response.result());
            assertEquals(TicketStatus.USED, validTicket.getStatus());
            verify(checkinRepository).save(any(Checkin.class));
            verify(ticketRepository).save(validTicket);
        }

        @Test
        @DisplayName("Гейт не найден → IllegalArgumentException")
        void gateNotFound() {
            when(checkinRepository.findByClientEventId(any())).thenReturn(Optional.empty());
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));
            when(checkinRepository.findByTicketId(1L)).thenReturn(Optional.empty());
            when(gateRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> ticketService.checkin(request, CheckinSource.ONLINE));
        }

        @Test
        @DisplayName("Гонка: DataIntegrityViolation → ALREADY_USED")
        void concurrencyRace() {
            when(checkinRepository.findByClientEventId(any())).thenReturn(Optional.empty());
            when(ticketRepository.findByCode(validCode)).thenReturn(Optional.of(validTicket));
            when(checkinRepository.findByTicketId(1L))
                    .thenReturn(Optional.empty())   // первый вызов — проверка
                    .thenReturn(Optional.of(createCheckin())); // второй — после гонки
            when(gateRepository.findById(1L)).thenReturn(Optional.of(gate));
            when(checkinRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("unique violation"));

            TicketResponse response = ticketService.checkin(request, CheckinSource.ONLINE);

            assertEquals(ValidationResult.ALREADY_USED, response.result());
            assertNotNull(response.previousCheckin());
        }

        private Checkin createCheckin() {
            Checkin c = new Checkin();
            c.setCheckedAt(LocalDateTime.of(2026, 6, 1, 10, 0));
            c.setGate(gate);
            c.setTicket(validTicket);
            return c;
        }
    }
}