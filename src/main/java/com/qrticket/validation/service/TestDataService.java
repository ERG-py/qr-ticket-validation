package com.qrticket.validation.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.qrticket.validation.dto.GenerateTicketsResponse;
import com.qrticket.validation.dto.GeneratedTicketInfo;
import com.qrticket.validation.entity.Ticket;
import com.qrticket.validation.entity.TicketStatus;
import com.qrticket.validation.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataService {

    private static final String QR_DIRECTORY = "generated-qr";
    private static final int QR_SIZE = 300;

    private final TicketRepository ticketRepository;

    @Transactional
    public GenerateTicketsResponse generateTickets(int count) {
        List<GeneratedTicketInfo> generated = new ArrayList<>();

        // Создаём папку для QR-картинок, если ещё нет
        Path qrDir = Paths.get(QR_DIRECTORY);
        try {
            Files.createDirectories(qrDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать папку " + QR_DIRECTORY, e);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime validTo = now.plusDays(30);

        for (int i = 1; i <= count; i++) {
            UUID code = UUID.randomUUID();
            String holderName = "Тест-" + i;

            // Сохраняем билет в БД
            Ticket ticket = new Ticket();
            ticket.setCode(code);
            ticket.setStatus(TicketStatus.VALID);
            ticket.setHolderName(holderName);
            ticket.setEventId(42L);
            ticket.setValidFrom(now);
            ticket.setValidTo(validTo);
            ticket.setCreatedAt(now);
            ticketRepository.save(ticket);

            // Генерируем QR-картинку
            try {
                BitMatrix matrix = new MultiFormatWriter()
                        .encode(code.toString(), BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
                Path filePath = qrDir.resolve(code + ".png");
                MatrixToImageWriter.writeToPath(matrix, "PNG", filePath);
            } catch (WriterException | IOException e) {
                log.error("Не удалось создать QR для {}: {}", code, e.getMessage());
            }

            generated.add(new GeneratedTicketInfo(code, holderName));
        }

        log.info("Сгенерировано {} тестовых билетов, QR в папке: {}",
                count, qrDir.toAbsolutePath());

        return new GenerateTicketsResponse(generated, qrDir.toAbsolutePath().toString());
    }
}