package com.ticketing.system.Presentation.session;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.vaadin.flow.server.VaadinSession;

import java.util.Collections;
import java.util.List;

/**
 * Session-scoped holder for the notification inbox delivered on login (UC-37).
 * Populated by LoginPresenter immediately after a successful login, read by
 * MainLayout to build the bell panel and badge count.
 */
public final class NotificationSession {

    private static final String NOTIFICATIONS_KEY = "notificationSession.notifications";

    private NotificationSession() {}

    /**
     * Store the pending notifications delivered on login. Replaces any previous list.
     * TODO(I.5 / #225): this is a login-only snapshot — notifications generated mid-session
     *  (PURCHASE_CONFIRMED, CART_EXPIRING, etc.) won't appear in the bell until the user
     *  logs out and back in. Real-time delivery is deferred to Grade ג'.
     */
    public static void store(List<NotificationDTO> notifications) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null || notifications == null) return;
        s.setAttribute(NOTIFICATIONS_KEY, List.copyOf(notifications));
    }

    /** All notifications delivered in this session, oldest-first. Empty list if none. */
    @SuppressWarnings("unchecked")
    public static List<NotificationDTO> getAll() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return Collections.emptyList();
        Object value = s.getAttribute(NOTIFICATIONS_KEY);
        return value instanceof List<?> list ? (List<NotificationDTO>) list : Collections.emptyList();
    }

    /** Count of notifications that have not been read yet (status != "READ"). */
    public static int getUnreadCount() {
        return (int) getAll().stream()
                .filter(n -> !"READ".equals(n.status()))
                .count();
    }

    /** Clear the inbox — call this on sign-out. */
    public static void clear() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(NOTIFICATIONS_KEY, null);
    }
}
