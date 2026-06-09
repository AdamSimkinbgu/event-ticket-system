package com.ticketing.system.Presentation.security;

import com.vaadin.flow.server.VaadinSession;

import java.util.Set;

/**
 * Session-scoped mock authentication state for the V2 placeholder UI.
 *
 * <p>Holds three independent flags on {@link VaadinSession}:
 * {@code signedIn} (member auth), {@code name} (display name), and
 * {@code admin} (platform-admin authority). The admin flag is
 * <em>independent</em> of signed-in state — a user can be only a member,
 * only an admin, or both. The unified {@code LoginView} decides which
 * flags to set based on whether the entered username appears in
 * {@link #ADMIN_USERNAMES}; the {@link com.ticketing.system.Presentation.dev.DevPanel}
 * lets developers toggle the flags freely for testing.
 *
 * <p>Real impl swaps for Spring Security's
 * {@code SecurityContextHolder.getContext().getAuthentication()} + a
 * {@code GrantedAuthority} check.
 */
public final class MockAuth {

    private static final String SIGNED_IN_KEY = "mockAuth.signedIn";
    private static final String NAME_KEY      = "mockAuth.name";
    private static final String ADMIN_KEY     = "mockAuth.admin";

    /**
     * Closed set of usernames that belong to the platform-admin pool.
     * The unified {@code LoginView} routes users with one of these
     * names into the admin path; {@code RegisterView} refuses them so
     * nobody can register a member account that shadows an admin name.
     */
    public static final Set<String> ADMIN_USERNAMES = Set.of(
        "admin", "platform.admin", "bar.miyara"
    );

    private MockAuth() { }

    public static boolean isAdminUsername(String name) {
        return name != null && ADMIN_USERNAMES.contains(name.trim().toLowerCase());
    }

    /** Member sign-in — leaves the admin flag untouched. */
    public static void signIn(String displayName) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.TRUE);
        s.setAttribute(NAME_KEY, displayName);
    }

    /** Admin sign-in — sets both signed-in and admin flags. */
    public static void signInAsAdmin(String displayName) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.TRUE);
        s.setAttribute(NAME_KEY, displayName);
        s.setAttribute(ADMIN_KEY, Boolean.TRUE);
    }

    /**
     * Toggle the admin flag independently of member sign-in. Used by the
     * dev panel so an admin can be coupled with a member persona.
     * Setting {@code true} on a signed-out session also signs the user
     * in with a default name.
     */
    public static void setAdmin(boolean isAdmin) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(ADMIN_KEY, isAdmin);
        if (isAdmin) {
            s.setAttribute(SIGNED_IN_KEY, Boolean.TRUE);
            if (s.getAttribute(NAME_KEY) == null) {
                s.setAttribute(NAME_KEY, "admin");
            }
        }
    }

    public static void signOut() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.FALSE);
        s.setAttribute(NAME_KEY, null);
        s.setAttribute(ADMIN_KEY, Boolean.FALSE);
    }

    public static boolean isSignedIn() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return false;
        return Boolean.TRUE.equals(s.getAttribute(SIGNED_IN_KEY));
    }

    public static boolean isAdmin() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return false;
        return Boolean.TRUE.equals(s.getAttribute(ADMIN_KEY));
    }

    public static String displayName() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (String) s.getAttribute(NAME_KEY);
    }
}
