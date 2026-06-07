package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;

import java.util.List;

/**
 * Date-range field — popover with preset list + custom range stub.
 * Ports the React {@code DateRangeField}.
 */
public class LkDateRangeField extends NativeLabel {

    private static final List<String> PRESETS = List.of(
        "Any time", "Today", "This weekend", "Next 7 days", "Next 30 days", "This month", "Next 3 months"
    );

    private final Span labelSpan = new Span();
    private final Span valueSpan = new Span();
    private final LkMenu menu = new LkMenu();
    private boolean hasLabel;
    private String currentValue;

    public LkDateRangeField() {
        this("Any time");
    }

    public LkDateRangeField(String initial) {
        this.currentValue = initial;
        addClassName("lk-field");
        labelSpan.addClassName("lk-label");
        valueSpan.addClassName("lk-input-val");
        valueSpan.setText(initial);

        Span trigger = new Span();
        trigger.addClassName("lk-input");
        trigger.addClassName("lk-select");
        Span affix = new Span();
        affix.addClassName("lk-input-affix");
        affix.add(new LkIcon("calendar", 16));
        trigger.add(valueSpan, affix);
        trigger.getElement().setAttribute("tabindex", "0");

        rebuild();

        // Custom range row at the bottom
        menu.add(new LkMenu.Divider());
        menu.add(new LkMenu.Label("Custom range"));
        Div rangeRow = new Div();
        rangeRow.addClassName("lk-daterow");
        Span start = new Span("Start date");
        start.addClassName("lk-input");
        start.addClassName("lk-mini");
        Span sep = new Span("→");
        sep.addClassName("lk-daterow-sep");
        Span end = new Span("End date");
        end.addClassName("lk-input");
        end.addClassName("lk-mini");
        rangeRow.add(start, sep, end);
        menu.add(rangeRow);

        add(new LkPopover(trigger, menu));
    }

    public LkDateRangeField label(String t) {
        labelSpan.setText(t);
        if (!hasLabel) {
            getElement().insertChild(0, labelSpan.getElement());
            hasLabel = true;
        }
        return this;
    }

    public LkDateRangeField width(String w) { getStyle().set("width", w); return this; }

    public String getValue() { return currentValue; }

    private void rebuild() {
        menu.removeAll();
        for (String preset : PRESETS) {
            LkMenu.Item item = new LkMenu.Item(preset);
            if (preset.equals(currentValue)) item.active();
            item.onClick(() -> {
                currentValue = preset;
                valueSpan.setText(preset);
                rebuild();
            });
            menu.add(item);
        }
    }
}
