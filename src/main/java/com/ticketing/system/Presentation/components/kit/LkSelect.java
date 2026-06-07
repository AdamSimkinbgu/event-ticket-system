package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popover-backed select. Trigger looks like {@link LkField}; panel is an
 * {@link LkMenu} of options. Ports the React {@code Select} component.
 */
public class LkSelect extends NativeLabel {

    private final Span labelSpan = new Span();
    private final Span req = new Span(" *");
    private final Span valueSpan = new Span();
    private final LkMenu menu = new LkMenu();
    private final Span trigger = new Span();
    private final LkPopover popover;
    private String currentValue;
    private Consumer<String> onChange;
    private boolean hasLabel;

    public LkSelect(String value, List<String> options) {
        this.currentValue = value;
        addClassName("lk-field");
        labelSpan.addClassName("lk-label");
        req.addClassName("lk-req");
        valueSpan.addClassName("lk-input-val");
        valueSpan.setText(value);

        trigger.addClassName("lk-input");
        trigger.addClassName("lk-select");
        trigger.add(valueSpan, new LkIcon("caret", 13));
        trigger.getElement().setAttribute("tabindex", "0");

        popover = new LkPopover(trigger, menu);
        rebuildOptions(options);
        add(popover);
    }

    public LkSelect label(String t) {
        labelSpan.setText(t);
        if (!hasLabel) {
            getElement().insertChild(0, labelSpan.getElement());
            hasLabel = true;
        }
        return this;
    }

    public LkSelect required() {
        if (req.getParent().isEmpty())
            labelSpan.add(req);
        return this;
    }

    public LkSelect width(String w) {
        getStyle().set("width", w);
        return this;
    }

    public LkSelect onChange(Consumer<String> handler) {
        this.onChange = handler;
        return this;
    }

    public String getValue() {
        return currentValue;
    }

    private void rebuildOptions(List<String> options) {
        menu.removeAll();
        for (String opt : options) {
            LkMenu.Item item = new LkMenu.Item(opt);
            if (opt.equals(currentValue))
                item.active();
            item.onClick(() -> {
                currentValue = opt;
                valueSpan.setText(opt);
                if (onChange != null)
                    onChange.accept(opt);
                rebuildOptions(options);
            });
            menu.add(item);
        }
    }
}
