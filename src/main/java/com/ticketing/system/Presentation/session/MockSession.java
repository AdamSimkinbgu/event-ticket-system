package com.ticketing.system.Presentation.session;

import com.vaadin.flow.server.VaadinSession;

/**
 * Session-scoped state for "which of the user's companies is currently
 * in focus". Drives capability resolution and UI defaults (the owner
 * dashboard subtitle, the venue editor's company picker, the policy
 * scope toolbar).
 *
 * <p>When the user owns exactly one company, that one is implicitly
 * current. When they own several, a future company-switcher in the
 * topbar will call {@link #setCurrentCompany(String)} as the user picks.
 */
public final class MockSession {

    private static final String CURRENT_COMPANY_KEY = "mockSession.currentCompanyId";

    private MockSession() { }

    /** Currently-selected company id, or {@code null} when nothing is set / no session. */
    public static String currentCompanyId() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return null;
        return (String) s.getAttribute(CURRENT_COMPANY_KEY);
    }

    /** Persist the user's company selection across navigations. */
    public static void setCurrentCompany(String companyId) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(CURRENT_COMPANY_KEY, companyId);
    }

    public static void clearCurrentCompany() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) s.setAttribute(CURRENT_COMPANY_KEY, null);
    }

    /**
     * The Company record currently in focus. Falls back to the first
     * company owned by the user when no explicit selection is stored.
     * Returns {@code null} when the user owns nothing.
     */
    public static MockCompanies.Company currentCompany() {
        var all = MockCompanies.forCurrentUser();
        if (all.isEmpty()) return null;
        String id = currentCompanyId();
        if (id != null) {
            for (var c : all) if (id.equals(c.id())) return c;
        }
        return all.get(0);
    }
}
