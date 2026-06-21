package com.ticketing.system.unit.presentation;

import com.ticketing.system.Presentation.components.company.EditPermissionsDialog;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkConfirm;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.venue.VkSeat;
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.admin.AdminAnnouncementsView;
import com.ticketing.system.Presentation.views.admin.AdminComplaintQueueView;
import com.ticketing.system.Presentation.views.admin.AdminDashboardView;
import com.ticketing.system.Presentation.views.admin.GlobalHistoryView;
import com.ticketing.system.Presentation.views.admin.OrganizationalTreeView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.ticketing.system.Presentation.views.company.ManagerListView;
import com.ticketing.system.Presentation.presenters.company.ManagerListPresenter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

/**
 * Smoke tests for the V2 Presentation layer.
 *
 * <p>Lightweight checks — boots the Spring context but does not render any
 * view. Verifies:
 *
 * <ul>
 *   <li>Vaadin Flow classes are on the classpath</li>
 *   <li>{@link MainLayout} and {@link WorkspaceLayout} are well-formed</li>
 *   <li>Representative buyer + owner + platform-admin views construct
 *       without throwing (catches kit-API misuse)</li>
 *   <li>Custom kit components instantiate without throwing</li>
 *   <li>{@link Toasts} utility is reachable</li>
 * </ul>
 *
 * <p>Rendering tests are intentionally out of scope — the V2 spec exempts UI
 * tests ("בדיקות אינן נדרשות עבור ממשק המשתמש"). Business behavior is
 * exercised by the application-layer acceptance tests instead.
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
            "Vaadin AppLayout class not on classpath (needed by MainLayout / WorkspaceLayout)");
    }

    @Test
    void presentationPackageIsConfigured() {
        // All view classes must live under the package configured in
        // application.yml `vaadin.allowed-packages`, otherwise Vaadin's
        // route scanner won't discover them.
        assertTrue(BrowseEventsView.class.getPackageName()
                .startsWith("com.ticketing.system.Presentation"),
            "BrowseEventsView must live under com.ticketing.system.Presentation");
        assertTrue(AdminDashboardView.class.getPackageName()
                .startsWith("com.ticketing.system.Presentation"),
            "AdminDashboardView must live under com.ticketing.system.Presentation");
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
        assertNotNull(WorkspaceLayout.class, "WorkspaceLayout class did not load");
    }

    @Test
    void coreViewsInstantiate() {
        // Spot-check one MainLayout view and one WorkspaceLayout view as a
        // cheap canary for kit-API breakage.
        assertDoesNotThrow(BrowseEventsView::new, "BrowseEventsView (root route) failed to construct");
        assertDoesNotThrow(GlobalHistoryView::new, "GlobalHistoryView (admin route) failed to construct");
    }

    @Test
    void platformAdminViewsInstantiate() {
        // Every PlatformAdminLayout view. Sign-in is now the unified LoginView
        // (which is exercised by the buyer-side construction path).
        assertDoesNotThrow(AdminDashboardView::new,      "AdminDashboardView failed to construct");
        assertDoesNotThrow(AdminAnnouncementsView::new,  "AdminAnnouncementsView failed to construct");
        assertDoesNotThrow(AdminComplaintQueueView::new, "AdminComplaintQueueView failed to construct");
        assertDoesNotThrow(OrganizationalTreeView::new,  "OrganizationalTreeView failed to construct");
    }

    @Test
    void managerListViewInstantiates() {
        // Owner-workspace view wired to a presenter (#264). A mock presenter
        // returning an empty success roster exercises the grid-building path
        // without a UI context — a canary for kit-API breakage.
        ManagerListPresenter presenter = mock(ManagerListPresenter.class);
        when(presenter.loadRoster(any())).thenReturn(
            new ManagerListPresenter.Outcome.Success("Acme", List.of(), List.of()));
        assertDoesNotThrow(() -> new ManagerListView(presenter),
            "ManagerListView failed to construct");
    }

    @Test
    void managerActionDialogsConstruct() {
        // V2-CADMIN-03 dialogs — build path only (open() needs a UI context).
        assertDoesNotThrow(() -> new LkConfirm("Revoke manager", "Remove Carol?",
                LkConfirm.Severity.danger)
                .confirmText("Revoke"),
            "LkConfirm failed to construct");
        assertDoesNotThrow(() -> new EditPermissionsDialog(
                "carol", "Manager", List.of("VIEW_SALES"), names -> { }),
            "EditPermissionsDialog failed to construct");
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
