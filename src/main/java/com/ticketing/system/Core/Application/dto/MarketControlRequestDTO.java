package com.ticketing.system.Core.Application.dto;

// Input to SystemAdminService.openMarket() / closeMarket() (UC-32).
// 'reason' is recorded for the audit log (no Close UC explicitly defined; see open question).
public record MarketControlRequestDTO(
    String action,                       // "OPEN" / "CLOSE"
    String reason
) {}
