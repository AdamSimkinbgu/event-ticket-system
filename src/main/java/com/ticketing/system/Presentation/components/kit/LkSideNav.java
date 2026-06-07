package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Aside;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Left drawer nav for the admin / owner shell. Heading + list of
 * icon+label items. Ports the React {@code SideNav}.
 */
public class LkSideNav extends Aside {

    public record Item(String iconName, String label, Class<? extends Component> target) {
    }

    private final Div heading = new Div();
    private final List<Div> itemEls = new ArrayList<>();
    private String activeLabel;

    public LkSideNav(String headingText) {
        addClassName("lk-sidenav");
        heading.addClassName("lk-sidenav-h");
        heading.setText(headingText);
        add(heading);
    }

    public LkSideNav items(List<Item> items, String activeLabel) {
        this.activeLabel = activeLabel;
        for (Div el : itemEls)
            remove(el);
        itemEls.clear();

        for (Item it : items) {
            Div anchor;
            if (it.target != null) {
                RouterLink link = new RouterLink();
                link.setRoute(it.target);
                anchor = wrapRouterLink(link, it);
            } else {
                anchor = new Div();
                buildItemContent(anchor, it);
            }
            anchor.addClassName("lk-sidenav-item");
            if (it.label.equals(activeLabel))
                anchor.addClassName("on");
            add(anchor);
            itemEls.add(anchor);
        }
        return this;
    }

    private Div wrapRouterLink(RouterLink link, Item it) {
        Div anchor = new Div();
        buildItemContent(link, it);
        anchor.add(link);
        link.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "10px")
                .set("color", "inherit")
                .set("text-decoration", "none")
                .set("width", "100%");
        return anchor;
    }

    private void buildItemContent(Component container, Item it) {
        Span ico = new Span();
        ico.addClassName("lk-sidenav-ico");
        ico.add(new LkIcon(it.iconName, 18));
        Span lbl = new Span(it.label);
        container.getElement().appendChild(ico.getElement(), lbl.getElement());
    }

    /** Use the orange-accent admin variant. */
    public LkSideNav platform() {
        // Class added on the parent shell (lk-admin-platform on wrapper) drives this.
        return this;
    }
}
