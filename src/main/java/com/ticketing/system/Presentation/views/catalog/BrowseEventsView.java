package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkDateRangeField;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.catalog.BrowseEventsPresenter;
import com.ticketing.system.Presentation.session.SessionIdentity;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Route(value = "browse", layout = MainLayout.class)
@PageTitle("Browse Events · TicketHub")
@AnonymousAllowed
public class BrowseEventsView extends LkPage implements BeforeEnterObserver {

    // ---- data model ----

    private record EventRow(String name, String category, String venue,
            String date, LocalDateTime startsAt, int priceCents,
            LkStatusDot.Tone tone, String status, String id) {
    }

    /** A resolved from/to date range for a date-range preset; either bound may be null (unbounded). */
    record DateWindow(LocalDate from, LocalDate to) { }

    private final BrowseEventsPresenter presenter;
    private final SessionIdentity identity;

    private static final String CAT_ALL     = "All categories";
    private static final String COUNTRY_ALL = "All countries";
    private static final String CITY_ALL    = "All cities";
    private static final String DATE_ANY    = "Any time";

    private static final List<String> CATEGORIES = List.of(
            CAT_ALL, "Concerts", "Sports", "Theatre", "Festivals", "Comedy");

    private static final Map<String, List<String>> CITIES_BY_COUNTRY = Map.of(
            "Israel", List.of("Tel Aviv", "Jerusalem", "Haifa", "Caesarea", "Be'er Sheva"),
            "USA",    List.of("New York", "Los Angeles", "Chicago"),
            "UK",     List.of("London", "Manchester", "Birmingham"));

    private static final List<String> COUNTRIES = List.of(COUNTRY_ALL, "Israel", "USA", "UK");

    private static final List<String> SORTS = List.of(
            "Date · soonest", "Date · latest", "Price · low to high", "Price · high to low", "Popularity");

    // ---- filter state ----

    private String filterCategory  = CAT_ALL;
    private String filterCountry   = COUNTRY_ALL;
    private String filterCity      = CITY_ALL;
    private String filterArtist    = "";
    private String filterDateRange = DATE_ANY;
    private String filterSort      = SORTS.get(0);
    private Integer filterPriceMin = null;
    private Integer filterPriceMax = null;

    // ---- ui references for re-render / reset ----

    private LkSelect categorySelect;
    private LkSelect countrySelect;
    private LkSelect citySelect;
    private LkSelect sortSelect;
    private LkDateRangeField dateRangeField;
    private DatePicker customFromPicker;
    private DatePicker customToPicker;
    private Div customRangeRow;
    private TextField artistField;
    private IntegerField priceMin;
    private IntegerField priceMax;
    private Div gridSlot;
    private Span resultsCountSpan;

    public BrowseEventsView(BrowseEventsPresenter presenter, SessionIdentity identity) {
        this.presenter = presenter;
        this.identity = identity;

        List<EventRow> allEvents = presenter.search(identity.credential(), CatalogSearchFiltersDTO.empty())
                .stream().map(BrowseEventsView::toRow).toList();

        add(buildHero());
        add(buildAllEventsHeader());
        add(buildBrowseSplit());
        renderGrid(allEvents.stream().sorted(comparatorFor(filterSort)).toList());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
            .getParameters().getOrDefault("category", List.of());
        if (values.isEmpty()) return;
        String category = values.get(0);
        if (CATEGORIES.contains(category)) setCategory(category);
    }

    // ---- hero ----

    private Component buildHero() {
        Div hero = new Div();
        hero.addClassName("bz-hero");
        Div title = new Div();
        title.addClassName("bz-hero-title");
        title.setText("Find your next experience");
        Div sub = new Div();
        sub.addClassName("bz-hero-sub");
        sub.setText("Concerts, matches, theatre and conferences — across Israel.");
        hero.add(title, sub);
        return hero;
    }

    // ---- category selection ----

    private void setCategory(String category) {
        if (filterCategory.equals(category)) return;
        filterCategory = category;
        if (categorySelect != null) categorySelect.setValue(category);
        runSearch();
    }

    // ---- "All events" header with live result count ----

    private Component buildAllEventsHeader() {
        LkRow row = new LkRow().align("baseline").gap(10);
        row.add(Lk.h2("All Events"));
        resultsCountSpan = Lk.muted("");
        resultsCountSpan.getStyle().set("font-size", "13.5px");
        row.add(resultsCountSpan);
        return row;
    }

    // ---- filter sidebar + grid slot ----

    private Component buildBrowseSplit() {
        Div split = new Div();
        split.addClassName("bz-browse-split");
        split.add(buildFilters(), buildGridSlot());
        return split;
    }

    private Component buildFilters() {
        LkCard card = new LkCard("Filters").pad(18).flush();

        NativeButton clear = new NativeButton("Clear All");
        clear.addClassName("lk-link-btn");
        clear.addClickListener(e -> clearFilters());
        card.headerRight(clear);

        // Category
        categorySelect = new LkSelect(CAT_ALL, CATEGORIES).label("Category");
        categorySelect.onChange(this::setCategory);

        // Artist
        artistField = new TextField();
        artistField.setPlaceholder("e.g. Taylor Swift");
        artistField.setValueChangeMode(ValueChangeMode.LAZY);
        artistField.getStyle().set("width", "100%");
        artistField.addValueChangeListener(e -> {
            String v = e.getValue() == null ? "" : e.getValue();
            if (Objects.equals(filterArtist, v)) return;
            filterArtist = v;
            runSearch();
        });
        Div artistWrap = labelledWrap("Artist", artistField);

        // Date range — "Custom range" preset reveals the pickers below
        dateRangeField = new LkDateRangeField().label("Date Range");
        dateRangeField.onChange(v -> {
            if (Objects.equals(filterDateRange, v)) return;
            filterDateRange = v;
            customRangeRow.setVisible("Custom range".equals(v));
            if (!"Custom range".equals(v)) runSearch();
        });

        // Custom date pickers (shown only when "Custom range" is selected)
        customFromPicker = new DatePicker();
        customFromPicker.setPlaceholder("Start date");
        customFromPicker.addValueChangeListener(e -> {
            if (customFromPicker.getValue() != null && customToPicker.getValue() != null)
                runSearch();
        });

        customToPicker = new DatePicker();
        customToPicker.setPlaceholder("End date");
        customToPicker.addValueChangeListener(e -> {
            if (customFromPicker.getValue() != null && customToPicker.getValue() != null)
                runSearch();
        });

        customRangeRow = new Div();
        customRangeRow.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "8px");
        customFromPicker.getStyle().set("width", "100%");
        customToPicker.getStyle().set("width", "100%");
        customRangeRow.add(customFromPicker, customToPicker);
        customRangeRow.setVisible(false);

        // Country → City (dependent selects)
        countrySelect = new LkSelect(COUNTRY_ALL, COUNTRIES).label("Country");
        countrySelect.onChange(v -> {
            if (Objects.equals(filterCountry, v)) return;
            filterCountry = v;
            filterCity = CITY_ALL;
            citySelect.setOptions(cityOptionsFor(v));
            citySelect.enabled(!COUNTRY_ALL.equals(v));
            runSearch();
        });

        citySelect = new LkSelect(CITY_ALL, List.of(CITY_ALL)).label("City");
        citySelect.enabled(false);
        citySelect.onChange(v -> {
            if (Objects.equals(filterCity, v)) return;
            filterCity = v;
            runSearch();
        });

        // Sort
        sortSelect = new LkSelect(SORTS.get(0), SORTS).label("Sort By");
        sortSelect.onChange(v -> {
            if (Objects.equals(filterSort, v)) return;
            filterSort = v;
            runSearch();
        });

        LkCol col = new LkCol().gap(16);
        col.add(categorySelect, artistWrap, dateRangeField, customRangeRow,
                buildPriceRange(), countrySelect, citySelect, sortSelect);
        card.add(col);
        return card;
    }

    private Component buildPriceRange() {
        priceMin = new IntegerField();
        priceMin.setPlaceholder("Min");
        priceMin.setMin(0);
        priceMin.setMax(1000);
        priceMin.setStep(10);
        priceMin.setPrefixComponent(new Span("$"));
        priceMin.setValueChangeMode(ValueChangeMode.LAZY);
        priceMin.getStyle().set("flex", "1 1 0");
        priceMin.addValueChangeListener(e -> {
            if (Objects.equals(filterPriceMin, e.getValue())) return;
            filterPriceMin = e.getValue();
            runSearch();
        });

        priceMax = new IntegerField();
        priceMax.setPlaceholder("Max");
        priceMax.setMin(0);
        priceMax.setMax(1000);
        priceMax.setStep(10);
        priceMax.setPrefixComponent(new Span("$"));
        priceMax.setValueChangeMode(ValueChangeMode.LAZY);
        priceMax.getStyle().set("flex", "1 1 0");
        priceMax.addValueChangeListener(e -> {
            if (Objects.equals(filterPriceMax, e.getValue())) return;
            filterPriceMax = e.getValue();
            runSearch();
        });

        Div wrap = new Div();
        Span label = new Span("Price range");
        label.addClassName("lk-label");
        label.getStyle().set("display", "block").set("margin-bottom", "6px");
        wrap.add(label);
        LkRow row = new LkRow().gap(8);
        row.add(priceMin, priceMax);
        wrap.add(row);
        return wrap;
    }

    private Component buildGridSlot() {
        gridSlot = new Div();
        gridSlot.getStyle().set("min-width", "0").set("width", "100%");
        return gridSlot;
    }

    // ---- reset ----

    private void clearFilters() {
        // Reset state first — onChange handlers short-circuit when value == filterXxx.
        filterCategory  = CAT_ALL;
        filterCountry   = COUNTRY_ALL;
        filterCity      = CITY_ALL;
        filterArtist    = "";
        filterDateRange = DATE_ANY;
        filterSort      = SORTS.get(0);
        filterPriceMin  = null;
        filterPriceMax  = null;

        categorySelect.setValue(CAT_ALL);
        countrySelect.setValue(COUNTRY_ALL);
        citySelect.setOptions(List.of(CITY_ALL));
        citySelect.enabled(false);
        artistField.clear();
        dateRangeField.setValue(DATE_ANY);
        customRangeRow.setVisible(false);
        customFromPicker.clear();
        customToPicker.clear();
        sortSelect.setValue(SORTS.get(0));
        priceMin.clear();
        priceMax.clear();
        runSearch();
    }

    // ---- server-side search + render ----

    private void runSearch() {
        List<EventRow> rows = presenter.search(identity.credential(), currentFilters()).stream()
                .map(BrowseEventsView::toRow)
                .sorted(comparatorFor(filterSort))
                .toList();
        renderGrid(rows);
    }

    private CatalogSearchFiltersDTO currentFilters() {
        LocalDate from, to;
        if ("Custom range".equals(filterDateRange)) {
            from = customFromPicker.getValue();
            to   = customToPicker.getValue();
        } else {
            DateWindow dw = dateWindow(filterDateRange, LocalDate.now());
            from = dw.from();
            to   = dw.to();
        }
        return new CatalogSearchFiltersDTO(
                null,
                filterArtist.isBlank() ? null : filterArtist,
                categoryFilterValue(filterCategory),
                null,
                filterPriceMin == null ? null : filterPriceMin.doubleValue(),
                filterPriceMax == null ? null : filterPriceMax.doubleValue(),
                from, to,
                locationFilter(),
                null, null, null, null);
    }

    private String locationFilter() {
        return locationFilter(filterCity, filterCountry);
    }

    /** City (if selected) beats country so the DB predicate is as narrow as possible. */
    static String locationFilter(String filterCity, String filterCountry) {
        if (!CITY_ALL.equals(filterCity))       return filterCity;
        if (!COUNTRY_ALL.equals(filterCountry)) return filterCountry;
        return null;
    }

    /** UI category label → EventCategory enum name (null = no filter). */
    static String categoryFilterValue(String label) {
        if (label == null) return null;
        return switch (label) {
            case "Concerts"  -> "CONCERT";
            case "Sports"    -> "SPORTS";
            case "Theatre"   -> "THEATER";
            case "Festivals" -> "FESTIVAL";
            case "Comedy"    -> "COMEDY";
            default          -> null;
        };
    }

    /** Date-range preset → concrete [from, to] window relative to {@code today} (null bounds = open). */
    static DateWindow dateWindow(String preset, LocalDate today) {
        if (preset == null) return new DateWindow(null, null);
        return switch (preset) {
            case "Today" -> new DateWindow(today, today);
            case "This weekend" -> {
                LocalDate sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                yield new DateWindow(sat, sat.plusDays(1));
            }
            case "Next 7 days"   -> new DateWindow(today, today.plusDays(7));
            case "Next 30 days"  -> new DateWindow(today, today.plusDays(30));
            case "This month"    -> new DateWindow(today, today.with(TemporalAdjusters.lastDayOfMonth()));
            case "Next 3 months" -> new DateWindow(today, today.plusMonths(3));
            default              -> new DateWindow(null, null); // "Any time" and "Custom range"
        };
    }

    private void renderGrid(List<EventRow> rows) {
        gridSlot.removeAll();
        if (resultsCountSpan != null) {
            resultsCountSpan.setText(formatResultLine(rows.size()));
        }
        if (rows.isEmpty()) {
            gridSlot.add(buildEmptyState());
            return;
        }
        gridSlot.add(buildGrid(rows));
    }

    private String formatResultLine(int n) {
        StringBuilder s = new StringBuilder();
        s.append(n).append(" event").append(n == 1 ? "" : "s");
        if (!CAT_ALL.equals(filterCategory))
            s.append(" · ").append(filterCategory.toLowerCase());
        if (!filterArtist.isBlank())
            s.append(" · ").append(filterArtist);
        if (!CITY_ALL.equals(filterCity))
            s.append(" in ").append(filterCity);
        else if (!COUNTRY_ALL.equals(filterCountry))
            s.append(" in ").append(filterCountry);
        if (!DATE_ANY.equals(filterDateRange)) {
            if ("Custom range".equals(filterDateRange)
                    && customFromPicker.getValue() != null && customToPicker.getValue() != null) {
                s.append(" · ").append(customFromPicker.getValue())
                 .append(" → ").append(customToPicker.getValue());
            } else if (!"Custom range".equals(filterDateRange)) {
                s.append(" · ").append(filterDateRange.toLowerCase());
            }
        }
        if (filterPriceMin != null || filterPriceMax != null) {
            s.append(" · $")
                    .append(filterPriceMin == null ? "0" : filterPriceMin)
                    .append("–$")
                    .append(filterPriceMax == null ? "∞" : filterPriceMax);
        }
        return s.toString();
    }

    private Component buildGrid(List<EventRow> events) {
        LkGrid grid = new LkGrid()
                .col("Event", "name")
                .col("Category", "cat")
                .col("Venue", "venue")
                .col("Date", "date")
                .col("From", "from", LkGrid.Align.RIGHT)
                .col("Status", "status")
                .col("", "act", LkGrid.Align.RIGHT);
        for (EventRow ev : events)
            addRow(grid, ev);
        grid.build();
        return grid;
    }

    private void addRow(LkGrid grid, EventRow ev) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(ev.name) + "</b>");
        row.put("name", name);
        row.put("cat", ev.category);
        row.put("venue", ev.venue);
        row.put("date", ev.date);
        row.put("from", "$" + (ev.priceCents / 100));
        row.put("status", new LkStatusDot(ev.tone, ev.status));
        LkBtn reserve = new LkBtn("Reserve")
                .variant(LkBtn.Variant.primary)
                .size(LkBtn.Size.s)
                .onClick(e -> UI.getCurrent().navigate("events/" + ev.id));
        row.put("act", reserve);
        grid.row(row);
    }

    private Component buildEmptyState() {
        Span empty = new Span("No events match your filters. Try widening category, region, dates, or price.");
        empty.getStyle()
                .set("display", "block").set("padding", "48px").set("text-align", "center")
                .set("color", "var(--muted)").set("background", "#fff")
                .set("border", "1px dashed var(--border-strong)").set("border-radius", "12px");
        return empty;
    }

    // ---- helpers ----

    static List<String> cityOptionsFor(String country) {
        List<String> cities = CITIES_BY_COUNTRY.get(country);
        if (cities == null) return List.of(CITY_ALL);
        List<String> opts = new ArrayList<>(cities.size() + 1);
        opts.add(CITY_ALL);
        opts.addAll(cities);
        return opts;
    }

    private static Div labelledWrap(String labelText, Component field) {
        Div wrap = new Div();
        Span label = new Span(labelText);
        label.addClassName("lk-label");
        label.getStyle().set("display", "block").set("margin-bottom", "6px");
        wrap.add(label, field);
        return wrap;
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM");

    private static EventRow toRow(EventSummaryDTO dto) {
        LocalDateTime start = (dto.showDates() == null || dto.showDates().isEmpty())
                ? null : dto.showDates().get(0).startsAt();
        String date = start == null ? "TBA" : start.format(DATE_FMT);
        int priceCents = (int) Math.round(dto.minPrice() * 100);
        LkStatusDot.Tone tone = dto.soldOut() ? LkStatusDot.Tone.muted : LkStatusDot.Tone.ok;
        String status = dto.soldOut() ? "Sold out" : "On sale";
        return new EventRow(dto.name(), prettyCategory(dto.category()), dto.location(),
                date, start, priceCents, tone, status, String.valueOf(dto.eventId()));
    }

    private static Comparator<EventRow> comparatorFor(String sort) {
        Comparator<EventRow> bySoonest = Comparator.comparing(EventRow::startsAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<EventRow> byLatest = Comparator.comparing(EventRow::startsAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort) {
            case "Date · latest"       -> byLatest;
            case "Price · low to high" -> Comparator.comparingInt(EventRow::priceCents);
            case "Price · high to low" -> Comparator.comparingInt(EventRow::priceCents).reversed();
            case "Popularity"          -> Comparator.comparing(EventRow::id);
            default                    -> bySoonest;
        };
    }

    private static String prettyCategory(String enumName) {
        if (enumName == null) return "Other";
        return switch (enumName.toUpperCase()) {
            case "CONCERT", "MUSIC" -> "Concerts";
            case "SPORTS"           -> "Sports";
            case "THEATER"          -> "Theatre";
            case "FESTIVAL"         -> "Festivals";
            case "COMEDY"           -> "Comedy";
            default                 -> "Other";
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
