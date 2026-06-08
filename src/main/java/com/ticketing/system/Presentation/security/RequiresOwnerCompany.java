package com.ticketing.system.Presentation.security;

/**
 * Marker interface — owner-workspace views implement this to opt in to
 * the "must own at least one company" gate in {@link AuthBootstrap}.
 *
 * <p>Views deliberately <i>without</i> this marker:
 * <ul>
 *   <li>{@code MyCompaniesView} — the user's own list of companies
 *       (renders an empty state when they own none).</li>
 *   <li>{@code CompanyRegistrationView} — the redirect target when a
 *       non-owner tries to enter a gated view, so marking it would
 *       cause an infinite redirect.</li>
 * </ul>
 */
public interface RequiresOwnerCompany { }
