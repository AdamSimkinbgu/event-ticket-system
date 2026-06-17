package com.ticketing.system.Presentation;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;

/**
 * Application shell for the TicketHub Vaadin app.
 *
 * <p>The {@code @Theme("tickethub")} annotation activates the custom theme
 * at {@code src/main/frontend/themes/tickethub/} — its {@code styles.css}
 * (ported verbatim from {@code docs/tickethub-ui/TicketHub UI.html})
 * supplies every {@code .lk-*}, {@code .bz-*}, {@code .vk-*}, {@code .pe-*},
 * {@code .md-*}, {@code .ow-*}, {@code .acct-*}, {@code .auth-*},
 * {@code .org-*} class name that the Java component kit (in
 * {@code Presentation/components/kit/}) attaches to its rendered DOM.
 */
@Theme("tickethub")
public class AppShell implements AppShellConfigurator {
}
