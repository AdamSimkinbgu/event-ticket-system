package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to MessagingService.announce() (II.6.3.2 — admin outreach).
// 'audienceType' selects the recipients:
//   "ALL_MEMBERS" / "BROADCAST_MEMBERS" — every registered member
//   "MEMBER_LIST"                       — the members in audienceMemberIds
//   "PRODUCERS"                         — the companies in audienceCompanyIds
//                                         (empty list = all active companies)
public record AnnouncementRequestDTO(
    int adminId,
    String subject,
    String body,
    String audienceType,              // ParticipantType-like value as string
    List<Integer> audienceMemberIds,  // populated when audienceType is MEMBER_LIST
    List<Integer> audienceCompanyIds  // populated when audienceType is PRODUCERS
) {}
