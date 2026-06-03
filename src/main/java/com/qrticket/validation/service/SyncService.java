package com.qrticket.validation.service;

import com.qrticket.validation.dto.CheckinRequest;
import com.qrticket.validation.dto.SyncRequest;
import com.qrticket.validation.dto.SyncResponse;
import com.qrticket.validation.dto.SyncResultItem;
import com.qrticket.validation.dto.TicketResponse;
import com.qrticket.validation.entity.CheckinSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final TicketService ticketService;

    public SyncResponse sync(SyncRequest request) {
        List<SyncResultItem> results = new ArrayList<>();

        for (CheckinRequest checkinRequest : request.checkins()) {
            try {
                TicketResponse response = ticketService.checkin(
                        checkinRequest, CheckinSource.OFFLINE_SYNC);
                results.add(SyncResultItem.success(
                        checkinRequest.clientEventId(), response));
            } catch (Exception e) {
                log.error("Sync: ошибка обработки элемента clientEventId={}: {}",
                        checkinRequest.clientEventId(), e.getMessage(), e);
                results.add(SyncResultItem.error(
                        checkinRequest.clientEventId(), e.getMessage()));
            }
        }

        return new SyncResponse(results);
    }
}