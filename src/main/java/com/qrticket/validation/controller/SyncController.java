package com.qrticket.validation.controller;

import com.qrticket.validation.dto.SyncRequest;
import com.qrticket.validation.dto.SyncResponse;
import com.qrticket.validation.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> sync(
            @Valid @RequestBody SyncRequest request) {
        SyncResponse response = syncService.sync(request);
        return ResponseEntity.ok(response);
    }
}