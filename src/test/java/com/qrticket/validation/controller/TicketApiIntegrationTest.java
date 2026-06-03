package com.qrticket.validation.controller;

import com.qrticket.validation.dto.*;
import com.qrticket.validation.entity.Gate;
import com.qrticket.validation.entity.Ticket;
import com.qrticket.validation.entity.TicketStatus;
import com.qrticket.validation.repository.GateRepository;
import com.qrticket.validation.repository.TicketRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TicketApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private GateRepository gateRepository;

    private static UUID ticketCode;

    @BeforeEach
    void setUp() {
        // Создаём гейт если ещё нет
        if (gateRepository.count() == 0) {
            Gate gate = new Gate();
            gate.setName("Тестовый вход");
            gateRepository.save(gate);
        }

        // Создаём билет если ещё нет
        if (ticketCode == null) {
            ticketCode = UUID.randomUUID();
            Ticket ticket = new Ticket();
            ticket.setCode(ticketCode);
            ticket.setStatus(TicketStatus.VALID);
            ticket.setHolderName("Тест Тестович");
            ticket.setEventId(1L);
            ticket.setValidFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
            ticket.setValidTo(LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
            ticket.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            ticketRepository.save(ticket);
        }
    }

    // ========== Полный сценарий: validate → checkin → повторный checkin ==========

    @Test
    @Order(1)
    @DisplayName("validate валидного билета → OK_TO_ENTER")
    void validateValid() {
        ValidateRequest request = new ValidateRequest(ticketCode);

        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/validate", request, TicketResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ValidationResult.OK_TO_ENTER, response.getBody().result());
        assertEquals("Тест Тестович", response.getBody().holderName());
        assertNull(response.getBody().previousCheckin());
    }

    @Test
    @Order(2)
    @DisplayName("checkin валидного билета → OK_TO_ENTER")
    void checkinValid() {
        Long gateId = gateRepository.findAll().iterator().next().getId();
        CheckinRequest request = new CheckinRequest(
                ticketCode, gateId, UUID.randomUUID(), Instant.now());

        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/checkin", request, TicketResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ValidationResult.OK_TO_ENTER, response.getBody().result());
    }

    @Test
    @Order(3)
    @DisplayName("повторный validate после checkin → ALREADY_USED")
    void validateAfterCheckin() {
        ValidateRequest request = new ValidateRequest(ticketCode);

        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/validate", request, TicketResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ValidationResult.ALREADY_USED, response.getBody().result());
        assertNotNull(response.getBody().previousCheckin());
    }

    @Test
    @Order(4)
    @DisplayName("повторный checkin → ALREADY_USED")
    void checkinAlreadyUsed() {
        Long gateId = gateRepository.findAll().iterator().next().getId();
        CheckinRequest request = new CheckinRequest(
                ticketCode, gateId, UUID.randomUUID(), Instant.now());

        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/checkin", request, TicketResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ValidationResult.ALREADY_USED, response.getBody().result());
    }

    // ========== validate несуществующего билета ==========

    @Test
    @Order(5)
    @DisplayName("validate несуществующего билета → NOT_FOUND")
    void validateNotFound() {
        ValidateRequest request = new ValidateRequest(UUID.randomUUID());

        ResponseEntity<TicketResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/validate", request, TicketResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ValidationResult.NOT_FOUND, response.getBody().result());
    }

    // ========== /sync с пачкой ==========

    @Test
    @Order(6)
    @DisplayName("/sync — пачка: OK + NOT_FOUND")
    void syncBatch() {
        // Новый билет для sync
        UUID syncCode = UUID.randomUUID();
        Ticket syncTicket = new Ticket();
        syncTicket.setCode(syncCode);
        syncTicket.setStatus(TicketStatus.VALID);
        syncTicket.setHolderName("Sync Тест");
        syncTicket.setEventId(2L);
        syncTicket.setValidFrom(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        syncTicket.setValidTo(LocalDateTime.now(ZoneOffset.UTC).plusDays(30));
        syncTicket.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        ticketRepository.save(syncTicket);

        Long gateId = gateRepository.findAll().iterator().next().getId();

        CheckinRequest ok = new CheckinRequest(
                syncCode, gateId, UUID.randomUUID(), Instant.now());
        CheckinRequest notFound = new CheckinRequest(
                UUID.randomUUID(), gateId, UUID.randomUUID(), Instant.now());

        SyncRequest syncRequest = new SyncRequest(List.of(ok, notFound));

        ResponseEntity<SyncResponse> response = restTemplate.postForEntity(
                "/api/v1/sync", syncRequest, SyncResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().results().size());
        assertEquals(ValidationResult.OK_TO_ENTER,
                response.getBody().results().get(0).result());
        assertEquals(ValidationResult.NOT_FOUND,
                response.getBody().results().get(1).result());
    }

    // ========== Ошибки → JSON ==========

    @Test
    @Order(7)
    @DisplayName("пустое тело → 400 JSON")
    void emptyBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/tickets/validate", entity, ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().error());
        assertEquals(400, response.getBody().status());
    }
    @Test
    @Order(8)
    @DisplayName("несуществующий URL → 404 JSON")
    void notFoundUrl() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                "/api/v1/blabla", ErrorResponse.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
    }
}