package com.qrticket.validation.dto;

import java.util.List;

public record SyncResponse(
        List<SyncResultItem> results
) {}