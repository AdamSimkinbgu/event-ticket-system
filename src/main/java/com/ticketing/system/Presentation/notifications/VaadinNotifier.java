package com.ticketing.system.Presentation.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.INotifier;
import com.ticketing.system.Core.Application.services.NotificationDispatchService;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationType;
import com.ticketing.system.Presentation.components.Toasts;

/**
 * Presentation adapter implementing the `INotifier` port.
 *
 * Behaviour (minimal V2 offline path):
 * - Shows a Vaadin toast for the incoming `Notification` (best-effort).
 * - Delegates to `NotificationDispatchService.storePending` to persist the
 *   notification for later delivery (UC-36 offline path).
 */
@Component
public class VaadinNotifier implements INotifier {

    private final NotificationDispatchService notificationDispatchService;
    private final INotificationService notificationService; // injected for future use

    @Autowired
    public VaadinNotifier(NotificationDispatchService notificationDispatchService,
            INotificationService notificationService) {
        this.notificationDispatchService = notificationDispatchService;
        this.notificationService = notificationService;
    }

    @Override
    public void notifyUser(Notification notification) {
        // Show a best-effort UI toast for the recipient (if this runs in the
        // recipient's UI thread). Keep mapping simple based on type name.
        try {
            NotificationType type = notification.getType();
            String msg = notification.getMessage();
            String t = type == null ? "" : type.name();

            if (t.contains("SUCCESS") || t.contains("CONFIRMED") || t.contains("ISSUED")) {
                Toasts.success(msg);
            } else if (t.contains("FAIL") || t.contains("FAILED") || t.contains("ERROR")) {
                Toasts.failure(msg);
            } else if (t.contains("EXPIR") || t.contains("CANCEL") || t.contains("SOLD_OUT")) {
                Toasts.warn(msg);
            } else {
                Toasts.success(msg);
            }
        } catch (Exception e) {
            // Swallow UI errors — notifier should not crash business flows.
        }

        // Persist for offline delivery per UC-36. NotificationDispatchService
        // performs repository save and logging.
        try {
            notificationDispatchService.storePending(notification);
        } catch (Exception e) {
            // Best-effort: do not propagate to caller.
        }
    }
}
