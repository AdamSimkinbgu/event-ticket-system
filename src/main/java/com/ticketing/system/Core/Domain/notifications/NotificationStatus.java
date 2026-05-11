package com.ticketing.system.Core.Domain.notifications;

// Lifecycle states for a Notification (UC-36 design walkthrough).
//   PENDING    - stored offline, awaiting next-login delivery (UC-36)
//   DELIVERED  - pushed live (UC-35 online branch) or delivered on login (UC-37)
//   READ       - user has opened/read in UI
public enum NotificationStatus {
    PENDING,
    DELIVERED,
    READ
}
