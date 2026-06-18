package com.ticketing.system.Presentation.session;

import com.vaadin.flow.server.VaadinSession;

public class AuthSession {
    private static final String TOKEN_KEY = "authSession.token";

    private AuthSession() { }

    public static String token() {
        VaadinSession s = VaadinSession.getCurrent();
        return s == null ? null : (String) s.getAttribute(TOKEN_KEY);
    }

    public static void store(String token) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(TOKEN_KEY, token);
    }

    public static void clear() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(TOKEN_KEY, null);
    }
}
