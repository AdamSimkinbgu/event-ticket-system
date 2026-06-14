package com.ticketing.system.Core.Application.interfaces;

import com.ticketing.system.Core.Domain.notifications.Notification;

/**
 * Port for UI notification adapters (V2).
 */
public interface INotifier {

    /**
     * Notify the recipient (presentation-level adapter). Implementations may
     * show a UI toast and/or delegate to persistence for offline delivery.
     */
    void notifyUser(Notification notification);
}
