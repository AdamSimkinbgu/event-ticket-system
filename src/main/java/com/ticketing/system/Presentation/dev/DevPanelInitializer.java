package com.ticketing.system.Presentation.dev;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Attaches the {@link DevPanel} floating trigger pill to every UI on
 * init. Spring discovers this bean and registers it during Vaadin's
 * service init phase, the same way {@code AuthBootstrap} attaches its
 * navigation guard.
 *
 * <p>Removing this file (or its {@code @Component}) hides the dev
 * widget without touching any view code.
 */
@Component
public class DevPanelInitializer implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit ->
            uiInit.getUI().add(DevPanel.trigger())
        );
    }
}
