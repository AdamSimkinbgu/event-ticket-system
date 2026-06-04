package com.ticketing.system.unit.presentation;

import com.ticketing.system.Presentation.views.WelcomeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-F-01 smoke test: prove that adding Vaadin Flow to the project produces a
 * working Spring context and the temporary {@link WelcomeView} is well-formed.
 *
 * <p>This is intentionally lightweight — it boots the full Spring context but
 * doesn't render any view. Vaadin servlets are registered as
 * {@code ServletRegistrationBean}s rather than top-level beans, so we don't
 * assert on a specific bean type — instead we verify the classpath is correct
 * (Vaadin classes load) and the {@link WelcomeView} lives in the
 * configured {@code vaadin.allowed-packages} root.
 */
@SpringBootTest
@ActiveProfiles("test")
class VaadinSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void springContextLoadsWithVaadin() {
        // Reaching this method means @SpringBootTest succeeded. If Vaadin's
        // auto-configuration were broken or the starter were missing from the
        // classpath, the context load would have thrown before we got here.
        assertNotNull(context, "Spring context did not load");
    }

    @Test
    void vaadinClassesAreReachable() {
        // If vaadin-spring-boot-starter is on the classpath, these resolve.
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.spring.SpringServlet"),
            "Vaadin SpringServlet class not on classpath");
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.server.VaadinService"),
            "Vaadin VaadinService class not on classpath");
        assertDoesNotThrow(() -> Class.forName("com.vaadin.flow.component.UI"),
            "Vaadin UI class not on classpath");
    }

    @Test
    void welcomeViewIsInAllowedPackage() {
        // WelcomeView must live under the package configured in
        // application.yml `vaadin.allowed-packages`. If it moves outside,
        // Vaadin's route scan will miss it and the V2-F-01 smoke test fails
        // even though the rest of the system would compile.
        assertNotNull(WelcomeView.class);
        assertTrue(WelcomeView.class.getPackageName()
                .startsWith("com.ticketing.system.Presentation"),
            "WelcomeView must live under com.ticketing.system.Presentation");
    }
}
