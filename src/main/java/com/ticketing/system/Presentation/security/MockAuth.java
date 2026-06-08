package com.ticketing.system.Presentation.security;

import com.vaadin.flow.server.VaadinSession;

import java.util.Set;

/**
 * Session-scoped mock authentication state for the V2 placeholder UI.
 *
 * <p>Holds a signed-in flag, display name, and a role string ("member" or
 * "admin"). Admins live in a separate pool from members — they cannot
 * sign in through the regular {@code LoginView} and members cannot sign
 * in through {@code AdminLoginView}. The check is username-based via
 * {@link #ADMIN_USERNAMES} until V2-AUTH-02 lands a real authority model.
 *
 * <p>Real impl will replace this with
 * {@code SecurityContextHolder.getContext().getAuthentication()} and a
 * {@code GrantedAuthority} check on a database-backed user pool.
 */
public final class MockAuth {

    private static final String SIGNED_IN_KEY = "mockAuth.signedIn";
    private static final String NAME_KEY      = "mockAuth.name";
    private static final String ROLE_KEY      = "mockAuth.role";

    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_ADMIN  = "admin";

    /**
     * Closed set of usernames that belong to the platform-admin pool.
     * Members can never register with these names, never sign in with
     * them on the regular form, and admins can never sign in as members.
     */
    public static final Set<String> ADMIN_USERNAMES = Set.of(
        "admin", "platform.admin", "bar.miyara"
    );

    private MockAuth() { }

    /** True if the given username is reserved for the platform-admin pool. */
    public static boolean isAdminUsername(String name) {
        return name != null && ADMIN_USERNAMES.contains(name.trim().toLowerCase());
    }

    public static void signIn(String displayName) {
        signIn(displayName, ROLE_MEMBER);
    }

    public static void signInAsAdmin(String displayName) {
        signIn(displayName, ROLE_ADMIN);
    }

    private static void signIn(String displayName, String role) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.TRUE);
        s.setAttribute(NAME_KEY, displayName);
        s.setAttribute(ROLE_KEY, role);
    }

    public static void signOut() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return;
        s.setAttribute(SIGNED_IN_KEY, Boolean.FALSE);
        s.setAttribute(NAME_KEY, null);
        s.setAttribute(ROLE_KEY, null);
    }

    public static boolean isSignedIn() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return false;
        return Boolean.TRUE.equals(s.getAttribute(SIGNED_IN_KEY));
    }

    public static boolean isAdmin() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return false;
        return isSignedIn() && ROLE_ADMIN.equals(s.getAttribute(ROLE_KEY));
    }

    public static String role() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (String) s.getAttribute(ROLE_KEY);
    }

    public static String displayName() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (String) s.getAttribute(NAME_KEY);
    }
}
