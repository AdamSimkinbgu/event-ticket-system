package com.ticketing.system.Core.Application.dto;

// Input to MessagingService.startConversation() (II.3.10 — Member contacts a Company,
// or DIRECT thread between any two parties).
// 'type' is the ConversationType value as string.
public record StartConversationRequestDTO(
    int initiatorId,
    String initiatorType,            // ParticipantType value as string
    int counterpartyId,
    String counterpartyType,
    String type,                     // ConversationType
    String subject,
    String firstMessageBody
) {}
