package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.popover.PopoverPosition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kebab "⋮" overflow menu for grid rows. Ports the React {@code RowMenu}.
 * Configure items in insertion order; renders as an {@link LkIconBtn}
 * trigger with an {@link LkPopover} + {@link LkMenu} panel.
 */
public class LkRowMenu extends LkPopover {

    private final LkMenu menu;
    private final Map<String, Runnable> handlers = new LinkedHashMap<>();

    public LkRowMenu() {
        super(new LkIconBtn(new LkIcon("more", 16), "More actions"));
        menu = new LkMenu();
        content(menu);
        position(PopoverPosition.BOTTOM_END);
    }

    public LkRowMenu add(String label, Runnable handler) {
        LkMenu.Item item = new LkMenu.Item(label);
        item.onClick(handler);
        menu.add(item);
        handlers.put(label, handler);
        return this;
    }

    public LkRowMenu add(String iconName, String label, Runnable handler) {
        LkMenu.Item item = new LkMenu.Item(iconName, label);
        item.onClick(handler);
        menu.add(item);
        handlers.put(label, handler);
        return this;
    }

    public LkRowMenu danger(String iconName, String label, Runnable handler) {
        LkMenu.Item item = new LkMenu.Item(iconName, label);
        item.danger();
        item.onClick(handler);
        menu.add(item);
        handlers.put(label, handler);
        return this;
    }

    public LkRowMenu divider() {
        menu.add(new LkMenu.Divider());
        return this;
    }
}
