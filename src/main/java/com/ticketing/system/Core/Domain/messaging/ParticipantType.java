package com.ticketing.system.Core.Domain.messaging;

// What kind of entity is sending or receiving in a Conversation.
//   MEMBER             - a specific User (memberId)
//   COMPANY            - a specific ProductionCompany (companyId)
//   ADMIN              - a specific System Admin (adminId)
//   ADMIN_GROUP        - any System Admin (complaint queue)
//   BROADCAST_MEMBERS  - all platform Members (admin announcement target)
//   SYSTEM             - the platform itself (system-generated messages)
public enum ParticipantType {
    MEMBER,
    COMPANY,
    ADMIN,
    ADMIN_GROUP,
    BROADCAST_MEMBERS,
    SYSTEM
}
