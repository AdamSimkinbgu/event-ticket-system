package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;
import com.ticketing.system.Core.Domain.notifications.NotificationType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Seeds buyer and producer notifications so the notification bell and
 * the offline-delivery flow (UC-37) have something to render on first
 * load. PENDING entries are flushed on next login of their recipient;
 * DELIVERED entries form the notification history.
 *
 * <p>Writes directly to {@link INotificationRepository} — fabricated
 * historical timestamps are the whole point of seeded notification
 * history, so the dispatch-service path (which always stamps
 * {@code LocalDateTime.now()}) is bypassed.
 */
public final class DemoNotifications {

    private final INotificationRepository repository;
    private final Map<String, SeededUser> users;
    private final DemoClock clock;

    public DemoNotifications(INotificationRepository repository,
                             Map<String, SeededUser> users,
                             DemoClock clock) {
        this.repository = repository;
        this.users = users;
        this.clock = clock;
    }

    public void seed() {
        // -- 10 PENDING — flushed on next login of each recipient --
        pending(DemoUsers.AVI_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your tickets for Coldplay — Music of the Spheres are ready in your wallet.", 1);
        pending(DemoUsers.AVI_BUYER,   NotificationType.CART_EXPIRING,
            "Your cart for Beyoncé will release reserved tickets in 9 minutes.", 0);
        pending(DemoUsers.DANA_BUYER,  NotificationType.REFUND_ISSUED,
            "We've refunded ₪380 for your cancelled Hapoel tickets to your original card.", 3);
        pending(DemoUsers.DANA_BUYER,  NotificationType.TICKET_RESERVATION_SUCCESS,
            "Reservation confirmed — 1 ticket at Mediterranean Music Festival, VIP.", 2);
        pending(DemoUsers.IDO_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Coldplay tickets are confirmed. See you at Park HaYarkon!", 5);
        pending(DemoUsers.IDO_BUYER,   NotificationType.DIRECT_MESSAGE,
            "New reply from Live Nation Israel on your inquiry about parking.", 4);
        pending(DemoUsers.MAYA_BUYER,  NotificationType.EVENT_CANCELLED,
            "The Stand-Up Showcase on November 9 was rescheduled. Check your tickets for the new date.", 8);
        pending(DemoUsers.MAYA_BUYER,  NotificationType.PURCHASE_FAILED,
            "Your last attempted purchase for Hamlet didn't go through. Try again or contact support.", 6);
        pending(DemoUsers.NAIM_FOUNDER, NotificationType.EVENT_SOLD_OUT,
            "Coldplay — Music of the Spheres just sold out the GA zone.", 12);
        pending(DemoUsers.MOSHE_FOUNDER, NotificationType.OWNER_APPOINTMENT_PENDING,
            "You have a pending co-owner invitation at Shuni Productions awaiting your response.", 22);

        // -- 20 DELIVERED — notification history --
        delivered(DemoUsers.AVI_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Imagine Dragons tickets are confirmed.", 7);
        delivered(DemoUsers.AVI_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Mediterranean Music Festival tickets are confirmed.", 14);
        delivered(DemoUsers.AVI_BUYER,    NotificationType.TICKET_RESERVATION_SUCCESS,
            "Reservation confirmed — 2 tickets at Coldplay GA.", 21);
        delivered(DemoUsers.AVI_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Hapoel Jerusalem tickets are confirmed.", 35);
        delivered(DemoUsers.DANA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Beyoncé tickets are confirmed.", 9);
        delivered(DemoUsers.DANA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Imagine Dragons tickets are confirmed.", 18);
        delivered(DemoUsers.DANA_BUYER,   NotificationType.REFUND_ISSUED,
            "Refund issued for your Hapoel tickets.", 45);
        delivered(DemoUsers.DANA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Habima Othello tickets are confirmed.", 62);
        delivered(DemoUsers.IDO_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Beyoncé tickets are confirmed.", 10);
        delivered(DemoUsers.IDO_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Coldplay GA tickets are confirmed.", 28);
        delivered(DemoUsers.IDO_BUYER,    NotificationType.REMOVE_TICKET_RESERVATION_SUCCESS,
            "Reservation cancelled — Stand-Up Showcase, Bar zone.", 41);
        delivered(DemoUsers.IDO_BUYER,    NotificationType.PURCHASE_CONFIRMED,
            "Your Romeo & Juliet tickets are confirmed.", 75);
        delivered(DemoUsers.MAYA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Mediterranean Music Festival tickets are confirmed.", 12);
        delivered(DemoUsers.MAYA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Stand-Up Showcase tickets are confirmed.", 30);
        delivered(DemoUsers.MAYA_BUYER,   NotificationType.PURCHASE_CONFIRMED,
            "Your Hamlet tickets are confirmed.", 55);
        delivered(DemoUsers.MAYA_BUYER,   NotificationType.REFUND_ISSUED,
            "Refund issued for one of your past purchases.", 80);
        delivered(DemoUsers.NAIM_FOUNDER, NotificationType.EVENT_SOLD_OUT,
            "Beyoncé Renaissance just sold out across all zones.", 6);
        delivered(DemoUsers.MOSHE_FOUNDER, NotificationType.ROLE_CHANGED,
            "Mohamad's manager permissions were updated.", 33);
        delivered(DemoUsers.BENTZION_FOUNDER, NotificationType.EVENT_SOLD_OUT,
            "Romeo & Juliet sold out the Stalls zone for the opening night.", 17);
        delivered(DemoUsers.FAOUR_MANAGER, NotificationType.DIRECT_MESSAGE,
            "Three new inquiries from buyers about the Coldplay show.", 4);
    }

    private void pending(String recipientKey, NotificationType type, String message, int hoursAgo) {
        save(recipientKey, type, NotificationStatus.PENDING, message,
            LocalDateTime.ofInstant(clock.minusHours(hoursAgo), ZoneId.systemDefault()));
    }

    private void delivered(String recipientKey, NotificationType type, String message, int daysAgo) {
        save(recipientKey, type, NotificationStatus.DELIVERED, message,
            LocalDateTime.ofInstant(clock.minusDays(daysAgo), ZoneId.systemDefault()));
    }

    private void save(String recipientKey, NotificationType type, NotificationStatus status,
                      String message, LocalDateTime createdAt) {
        int recipientId = users.get(recipientKey).userId();
        String id = "demo-" + status.name().toLowerCase() + "-" + repository.nextId();
        repository.save(new Notification(id, recipientId, type, status, message, createdAt));
    }
}
