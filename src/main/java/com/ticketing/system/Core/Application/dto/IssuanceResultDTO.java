package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

// Output of ITicketIssuer.issue() (UC-34).
public record IssuanceResultDTO(
    String issuanceTransactionId,
    String issuerName,
    LocalDateTime issuedAt,
    List<BarcodeDTO> barcodes
) {}
