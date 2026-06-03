package com.qrticket.validation.service;

import com.qrticket.validation.dto.CheckinRequest;
import com.qrticket.validation.dto.PreviousCheckinInfo;
import com.qrticket.validation.dto.TicketResponse;
import com.qrticket.validation.entity.Checkin;
import com.qrticket.validation.entity.CheckinSource;
import com.qrticket.validation.entity.Gate;
import com.qrticket.validation.entity.Ticket;
import com.qrticket.validation.entity.TicketStatus;
import com.qrticket.validation.repository.CheckinRepository;
import com.qrticket.validation.repository.GateRepository;
import com.qrticket.validation.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CheckinRepository checkinRepository;
    private final GateRepository gateRepository;

    @Transactional(readOnly = true)
    public TicketResponse validate(UUID code) {
        // 1. Ищем билет по QR-коду
        Ticket ticket = ticketRepository.findByCode(code).orElse(null);
        if (ticket == null) {
            return TicketResponse.notFound();
        }

        // 2. Отменён?
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return TicketResponse.invalid();
        }

        // 3. Вне сроков действия?
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (ticket.getValidFrom() != null && now.isBefore(ticket.getValidFrom())) {
            return TicketResponse.invalid();
        }
        if (ticket.getValidTo() != null && now.isAfter(ticket.getValidTo())) {
            return TicketResponse.invalid();
        }

        // 4. Уже был проход?
        Optional<Checkin> existingCheckin = checkinRepository.findByTicketId(ticket.getId());
        if (existingCheckin.isPresent()) {
            Checkin last = existingCheckin.get();
            PreviousCheckinInfo previous = new PreviousCheckinInfo(
                    last.getCheckedAt().toInstant(ZoneOffset.UTC),
                    last.getGate().getId()
            );
            return TicketResponse.alreadyUsed(
                    ticket.getHolderName(), ticket.getEventId(), previous);
        }

        // 5. Всё чисто
        return TicketResponse.okToEnter(ticket.getHolderName(), ticket.getEventId());
    }

    @Transactional
    public TicketResponse checkin(CheckinRequest request, CheckinSource source) {
        // 1. Идемпотентность: этот clientEventId уже обработан?
        Optional<Checkin> duplicate = checkinRepository
                .findByClientEventId(request.clientEventId());
        if (duplicate.isPresent()) {
            Checkin existing = duplicate.get();
            PreviousCheckinInfo info = new PreviousCheckinInfo(
                    existing.getCheckedAt().toInstant(ZoneOffset.UTC),
                    existing.getGate().getId()
            );
            return TicketResponse.alreadyUsed(
                    existing.getTicket().getHolderName(),
                    existing.getTicket().getEventId(),
                    info);
        }

        // 2. Та же проверка, что в validate
        Ticket ticket = ticketRepository.findByCode(request.code()).orElse(null);
        if (ticket == null) {
            return TicketResponse.notFound();
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return TicketResponse.invalid();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (ticket.getValidFrom() != null && now.isBefore(ticket.getValidFrom())) {
            return TicketResponse.invalid();
        }
        if (ticket.getValidTo() != null && now.isAfter(ticket.getValidTo())) {
            return TicketResponse.invalid();
        }

        // 3. Уже был проход (реальный повторный скан, не дубль сети)?
        Optional<Checkin> existingCheckin = checkinRepository.findByTicketId(ticket.getId());
        if (existingCheckin.isPresent()) {
            Checkin last = existingCheckin.get();
            PreviousCheckinInfo previous = new PreviousCheckinInfo(
                    last.getCheckedAt().toInstant(ZoneOffset.UTC),
                    last.getGate().getId()
            );
            return TicketResponse.alreadyUsed(
                    ticket.getHolderName(), ticket.getEventId(), previous);
        }

        // 4. Всё чисто — фиксируем проход
        Gate gate = gateRepository.findById(request.gateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Gate not found: " + request.gateId()));

        Checkin checkin = new Checkin();
        checkin.setTicket(ticket);
        checkin.setGate(gate);
        checkin.setCheckedAt(request.checkedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
        checkin.setReceivedAt(now);
        checkin.setSource(source);
        checkin.setClientEventId(request.clientEventId());

        try {
            checkinRepository.save(checkin);
        } catch (DataIntegrityViolationException e) {
            // Гонка: другой поток вставил checkin между проверкой и INSERT.
            // UNIQUE на ticket_id не дал создать дубль — штатная ситуация.
            Checkin winner = checkinRepository.findByTicketId(ticket.getId())
                    .orElseThrow();
            PreviousCheckinInfo previous = new PreviousCheckinInfo(
                    winner.getCheckedAt().toInstant(ZoneOffset.UTC),
                    winner.getGate().getId()
            );
            return TicketResponse.alreadyUsed(
                    ticket.getHolderName(), ticket.getEventId(), previous);
        }

        // 5. Переводим билет в USED
        ticket.setStatus(TicketStatus.USED);
        ticketRepository.save(ticket);

        return TicketResponse.okToEnter(ticket.getHolderName(), ticket.getEventId());
    }
}