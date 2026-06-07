package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

/**
 * N-column form grid using CSS Grid. Ports {@code .lk-formgrid}.
 * Defaults to two columns; pass any number via constructor.
 */
public class LkFormGrid extends Div {

    public LkFormGrid(Component... children) { this(2, children); }

    public LkFormGrid(int cols, Component... children) {
        addClassName("lk-formgrid");
        getStyle().set("--cols", String.valueOf(cols));
        if (children != null && children.length > 0) add(children);
    }
}
