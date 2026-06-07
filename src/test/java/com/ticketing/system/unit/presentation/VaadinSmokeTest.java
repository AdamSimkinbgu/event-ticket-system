package com.ticketing.system.unit.presentation;

import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.venue.VkSeat;
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.ticketing.system.Presentation.views.admin.GlobalHistoryView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for V2-F-01 (Vaadin starter) and V2-F-02 (layouts + view stubs).
 *
 * <p>Lightweight checks — boots the Spring context but does not render any
 * view. Verifies:
 *
 * <ul>
 *   <li>Vaadin Flow classes are on the classpath</li>
 *   <li>{@link MainLayout} and {@link AdminLayout} are well-formed</li>
 *   <li>A representative placeholder ({@link BrowseEventsView},
 *       {@link GlobalHistoryView}) is reachable</li>
 *   <li>Custom components instantiate without throwing</li>
 *   <li>{@link Toasts} utility is reachable</li>
 * </ul>
 *
 * <p>Rendering tests are intentionally out of scope — the V2 spec exempts UI
 * tests ("בדיקות אינן נדרשות עבור ממשק המשתמש"). When lane owners replace
 * a placeholder with a real implementation, they exercise it via the
 * application-layer acceptance tests instead.
 */
@SpringBootTest
@ActiveProfiles("test")
class VaadinSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void springContextLoadsWithVaadin() {
        // Reaching this method means @SpringBootTest succeeded. If Vaadin's
        // auto-configuration were broken or the starter were missing, context
        // load would have thrown.
        assertNotNull(context, "Spring context did not load");
    }

    @Test
    void vaadinClassesAreReachable() {
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.spring.SpringServlet"),
            "Vaadin SpringServlet class not on classpath");
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.server.VaadinService"),
            "Vaadin VaadinService class not on classpath");
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.component.UI"),
            "Vaadin UI class not on classpath");
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.component.applayout.AppLayout"),
            "Vaadin AppLayout class not on classpath (needed by MainLayout / AdminLayout)");
    }

    @Test
    void presentationPackageIsConfigured() {
        // PlaceholderView and all subclasses must live under the package
        // configured in application.yml `vaadin.allowed-packages`.
        assertTrue(PlaceholderView.class.getPackageName()
                .startsWith("com.ticketing.system.Presentation"),
            "PlaceholderView must live under com.ticketing.system.Presentation");
        assertTrue(BrowseEventsView.class.getPackageName()
                .startsWith("com.ticketing.system.Presentation"),
            "BrowseEventsView must live under com.ticketing.system.Presentation");
    }

    @Test
    void layoutsAreLoadable() {
        // We can't instantiate the layouts here — they build RouterLinks in
        // their constructors and RouterLink calls VaadinService.getCurrent()
        // which is null outside a real UI context. So just verify the
        // classes load cleanly (proves imports + compilation are fine).
        // Actual layout rendering is verified by booting the app and
        // navigating to a route in a browser.
        assertNotNull(MainLayout.class, "MainLayout class did not load");
        assertNotNull(AdminLayout.class, "AdminLayout class did not load");
    }

    @Test
    void placeholderViewsInstantiate() {
        // Spot-check one MainLayout view and one AdminLayout view. If the
        // PlaceholderView base class is broken, every view subclass breaks.
        assertDoesNotThrow(BrowseEventsView::new, "BrowseEventsView (root route) failed to construct");
        assertDoesNotThrow(GlobalHistoryView::new, "GlobalHistoryView (admin route) failed to construct");
    }

    @Test
    void kitComponentsInstantiate() {
        // Generic kit primitives — these underpin every view.
        assertDoesNotThrow(() -> new LkIcon("ticket"),    "LkIcon failed");
        assertDoesNotThrow(() -> new LkBtn("Sign in"),    "LkBtn failed");
        assertDoesNotThrow(() -> new LkCard("Card"),      "LkCard failed");
        assertDoesNotThrow(() -> new LkBadge("OK"),       "LkBadge failed");
        // Domain components used by the venue / seat picker views.
        assertDoesNotThrow(() -> new VkSeat(VkSeat.State.free, "1"), "VkSeat failed");
        assertDoesNotThrow(VkSeatLegend::new,             "VkSeatLegend failed");
    }

    @Test
    void toastsUtilityIsLoadable() {
        // Just checking the class loads — we can't invoke Notification.show
        // outside a UI context.
        assertNotNull(Toasts.class);
    }
}
