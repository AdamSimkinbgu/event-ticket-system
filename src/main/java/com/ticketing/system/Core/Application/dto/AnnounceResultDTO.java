package com.ticketing.system.Core.Application.dto;

// Result of MessagingService.announce() (II.6.3.2 — admin outreach).
// An announcement fans out to one ANNOUNCEMENT conversation per recipient, so there is no single
// shared "broadcast id"; 'firstConversationId' is a representative conversation id from the fan-out.
public record AnnounceResultDTO(
    int recipientCount,
    String firstConversationId
) {}
