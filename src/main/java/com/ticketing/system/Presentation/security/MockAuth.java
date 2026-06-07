package com.ticketing.system.Presentation.security;

import com.vaadin.flow.server.VaadinSession;

/**
 * Session-scoped mock authentication state for the V2 placeholder UI.
 *
 * <p>Until V2-AUTH-02 (Adam) wires Spring Security, this utility stores
 * "signed in" + display name on the {@link VaadinSession} so every part of
 * the UI sees the same value: the global {@link AuthBootstrap} before-enter
 * guard, the {@code MainLayout} avatar persona, the {@code LoginView} /
 * {@code RegisterView} forms, and any presenter that needs to know.
 *
 * <p>Real impl will replace this with
 * {@code SecurityContextHolder.getContext().getAuthentication()}.
 */
public final class MockAuth {

    private static final String SIGNED_IN_KEY = "mockAuth.signedIn";
    private static final String NAME_KEY      = "mockAuth.name";

    private MockAuth() { }

    /** Mark the current session as signed in with the given display name. */
    public static void signIn(String displayName) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.TRUE);
        s.setAttribute(NAME_KEY, displayName);
    }

    /** Clear all auth attributes from the current session. */
    public static void signOut() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.FALSE);
        s.setAttribute(NAME_KEY, null);
    }

    /** True if the current session is signed in. False if no session yet. */
    public static boolean isSignedIn() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return false;
        return Boolean.TRUE.equals(s.getAttribute(SIGNED_IN_KEY));
    }

    /** Display name for the signed-in user, or {@code null}. */
    public static String displayName() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (String) s.getAttribute(NAME_KEY);
    }
}
