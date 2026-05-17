package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to MessagingService.announce() (II.6.3.2 — admin broadcasts).
// 'audienceType' selects between targeted group ("BROADCAST_MEMBERS", "PRODUCERS") or
// a specific list ("MEMBER_LIST" with audienceMemberIds populated).
public record AnnouncementRequestDTO(
    int adminId,
    String subject,
    String body,
    String audienceType,             // ParticipantType-like value as string
    List<Integer> audienceMemberIds  // populated only when audienceType is MEMBER_LIST
) {}
