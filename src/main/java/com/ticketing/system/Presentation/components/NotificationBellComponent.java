package com.ticketing.system.Presentation.components;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkNotifPanel;
import com.ticketing.system.Presentation.components.kit.LkPopover;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.NotificationSession;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.PopoverPosition;

import java.util.List;
import java.util.function.Consumer;

/**
 * Self-contained bell icon + unread badge + notification dropdown.
 * Reads NotificationSession on construction; call refresh() to rebuild
 * from the current session state (hook for future real-time delivery, #225).
 */
public class NotificationBellComponent extends Span {

    private final Consumer<String> onRead;

    public NotificationBellComponent(Consumer<String> onRead) {
        this.onRead = onRead;
        addAttachListener(e -> NotificationSession.setBell(this));
        addDetachListener(e -> NotificationSession.setBell(null));
        build();
    }

    /** Rebuild from current NotificationSession — safe to call after store(). */
    public void refresh() {
        removeAll();
        build();
    }

    private void build() {
        List<NotificationDTO> notifs = AuthSession.isSignedIn()
                ? NotificationSession.getAll().stream()
                        .sorted(java.util.Comparator.comparing(NotificationDTO::createdAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                        .toList()
                : List.of();

        int unread = (int) notifs.stream().filter(n -> !"READ".equals(n.status())).count();

        NativeButton bell = new NativeButton();
        bell.addClassName("lk-bell");
        bell.getElement().setAttribute("aria-label", "Notifications");
        bell.add(new LkIcon("bell", 18));

        if (unread > 0) {
            Span badge = new Span(String.valueOf(unread));
            badge.addClassName("lk-bell-badge");
            bell.add(badge);
        }

        add(new LkPopover(bell, LkNotifPanel.fromDTOs(notifs, onRead))
                .position(PopoverPosition.BOTTOM_END));
    }
}
