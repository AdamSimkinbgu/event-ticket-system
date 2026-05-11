package com.ticketing.system.Core.Domain.messaging;

// Categorizes a Conversation by its purpose. Drives RBAC and routing.
//   INQUIRY      - Member contacts a Company (II.3.10 / II.4.4)
//   COMPLAINT    - Member submits an integrity complaint to admins (II.3.3 / II.6.3.1)
//   ANNOUNCEMENT - Admin broadcasts to members (II.6.3.2)
//   DIRECT       - Generic direct conversation (catch-all / future)
public enum ConversationType {
    INQUIRY,
    COMPLAINT,
    ANNOUNCEMENT,
    DIRECT
}
