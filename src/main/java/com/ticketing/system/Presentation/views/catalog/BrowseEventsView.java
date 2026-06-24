package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Presentation.components.buyer.BzPoster;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkChip;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkDateRangeField;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.catalog.BrowseEventsPresenter;
import com.ticketing.system.Presentation.session.SessionIdentity;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
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

    private static final String CAT_ALL = "All categories";
    private static final String REGION_ALL = "All regions";
    private static final String DATE_ANY = "Any time";
    private static final List<String> CATEGORIES = List.of(CAT_ALL, "Concerts", "Sports", "Theatre", "Festivals", "Comedy");
    private static final List<String> REGIONS = List.of(REGION_ALL, "Tel Aviv", "Jerusalem", "Haifa", "Caesarea",
            "Be'er Sheva");
    private static final List<String> SORTS = List.of(
            "Date · soonest", "Date · latest", "Price · low to high", "Price · high to low", "Popularity");

    // ---- filter state ----

    private String filterCategory = CAT_ALL;
    private String filterRegion = REGION_ALL;
    private String filterDateRange = DATE_ANY;
    private String filterSort = SORTS.get(0);
    private Integer filterPriceMin = null;
    private Integer filterPriceMax = null;

    // ---- ui references for re-render / reset ----

    private final Map<LkChip, String> chipToCategory = new LinkedHashMap<>();
    private LkSelect categorySelect;
    private LkSelect regionSelect;
    private LkSelect sortSelect;
    private LkDateRangeField dateRangeField;
    private IntegerField priceMin;
    private IntegerField priceMax;
    private Div gridSlot;
    private Span resultsCountSpan;

    public BrowseEventsView(BrowseEventsPresenter presenter, SessionIdentity identity) {
        this.presenter = presenter;
        this.identity = identity;

        // One unfiltered server query seeds the "Featured this week" strip + the first grid render.
        List<EventRow> featured = presenter.search(identity.credential(), CatalogSearchFiltersDTO.empty())
                .stream().map(BrowseEventsView::toRow).toList();

        add(buildHero());
        add(buildCategoryChips());
        if (!featured.isEmpty()) {
            add(Lk.h2("Featured this week"));
            add(buildPosterGrid(featured));
        }
        add(buildAllEventsHeader());
        add(buildBrowseSplit());
        renderGrid(featured.stream().sorted(comparatorFor(filterSort)).toList());
    }

    /**
     * Apply a {@code ?category=...} query parameter (e.g. when arriving from a category card on the
     * landing page) by pre-selecting the matching chip + sidebar Category select, which re-runs the
     * server-side filtered query.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
            .getParameters().getOrDefault("category", List.of());
        if (values.isEmpty()) return;
        String category = values.get(0);
        // Only honor a known category (else the chips/select would mismatch).
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
        Div search = new Div();
        search.addClassName("bz-hero-search");
        search.add(new LkIcon("search", 17), new Span(" Search events, artists, venues…"));
        hero.add(title, sub, search);
        return hero;
    }

    // ---- category chips (synced with sidebar select) ----

    private Component buildCategoryChips() {
        LkRow row = new LkRow().gap(8);
        addChip(row, null, "All", CAT_ALL, true);
        addChip(row, "music", "Concerts", "Concerts", false);
        addChip(row, "trophy", "Sports", "Sports", false);
        addChip(row, "theater", "Theatre", "Theatre", false);
        addChip(row, "music", "Festivals", "Festivals", false);
        addChip(row, "mic", "Comedy", "Comedy", false);
        row.getStyle().set("margin", "4px 0 2px");
        return row;
    }

    private void addChip(LkRow row, String iconName, String label, String category, boolean active) {
        LkChip chip = new LkChip(label);
        if (iconName != null)
            chip.getElement().insertChild(0, new LkIcon(iconName, 15).getElement());
        if (active)
            chip.active();
        chip.getElement().addEventListener("click", e -> setCategory(category));
        chipToCategory.put(chip, category);
        row.add(chip);
    }

    private void setCategory(String category) {
        if (filterCategory.equals(category))
            return;
        filterCategory = category;
        chipToCategory.forEach((chip, cat) -> chip.active(cat.equals(category)));
        if (categorySelect != null)
            categorySelect.setValue(category);
        runSearch();
    }

    // ---- featured posters ----

    private Component buildPosterGrid(List<EventRow> featured) {
        Div grid = new Div();
        grid.addClassName("bz-poster-grid");
        featured.stream().limit(4).forEach(ev ->
                grid.add(new BzPoster(ev.category, ev.name, ev.venue + " · " + ev.date,
                        "From $" + (ev.priceCents / 100))
                        .onClick(() -> UI.getCurrent().navigate("events/" + ev.id))));
        return grid;
    }

    // ---- "All events" header with live result count ----

    private Component buildAllEventsHeader() {
        LkRow row = new LkRow().align("baseline").gap(10);
        row.add(Lk.h2("All events"));
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

        NativeButton clear = new NativeButton("Clear all");
        clear.addClassName("lk-link-btn");
        clear.addClickListener(e -> clearFilters());
        card.headerRight(clear);

        categorySelect = new LkSelect(CAT_ALL, CATEGORIES).label("Category");
        categorySelect.onChange(this::setCategory);

        dateRangeField = new LkDateRangeField().label("Date range");
        dateRangeField.onChange(v -> {
            if (Objects.equals(filterDateRange, v)) return;   // skip redundant re-query (e.g. clearFilters sync)
            filterDateRange = v;
            runSearch();
        });

        regionSelect = new LkSelect(REGION_ALL, REGIONS).label("Region");
        regionSelect.onChange(v -> {
            if (Objects.equals(filterRegion, v)) return;
            filterRegion = v;
            runSearch();
        });

        sortSelect = new LkSelect(SORTS.get(0), SORTS).label("Sort by");
        sortSelect.onChange(v -> {
            if (Objects.equals(filterSort, v)) return;
            filterSort = v;
            runSearch();
        });

        LkCol col = new LkCol().gap(16);
        col.add(categorySelect, dateRangeField, buildPriceRange(), regionSelect, sortSelect);
        card.add(col);
        return card;
    }

    private Component buildPriceRange() {
        Div wrap = new Div();
        Span label = new Span("Price range");
        label.addClassName("lk-label");
        label.getStyle().set("display", "block").set("margin-bottom", "6px");
        wrap.add(label);

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
        filterCategory = CAT_ALL;
        filterRegion = REGION_ALL;
        filterDateRange = DATE_ANY;
        filterSort = SORTS.get(0);
        filterPriceMin = null;
        filterPriceMax = null;

        chipToCategory.forEach((chip, cat) -> chip.active(cat.equals(CAT_ALL)));
        categorySelect.setValue(CAT_ALL);
        regionSelect.setValue(REGION_ALL);
        sortSelect.setValue(SORTS.get(0));
        dateRangeField.setValue(DATE_ANY);
        priceMin.clear();
        priceMax.clear();
        runSearch();
    }

    // ---- server-side search + render ----

    /** Build the filter DTO from current chip/select state, query the server, sort, render. */
    private void runSearch() {
        List<EventRow> rows = presenter.search(identity.credential(), currentFilters()).stream()
                .map(BrowseEventsView::toRow)
                .sorted(comparatorFor(filterSort))
                .toList();
        renderGrid(rows);
    }

    private CatalogSearchFiltersDTO currentFilters() {
        DateWindow dw = dateWindow(filterDateRange, LocalDate.now());
        return new CatalogSearchFiltersDTO(
                null,                                       // eventName
                null,                                       // artistName
                categoryFilterValue(filterCategory),        // category (enum name, or null = all)
                null,                                       // keywords
                filterPriceMin == null ? null : filterPriceMin.doubleValue(),
                filterPriceMax == null ? null : filterPriceMax.doubleValue(),
                dw.from(),
                dw.to(),
                REGION_ALL.equals(filterRegion) ? null : filterRegion,   // location (city/country substring)
                null, null, null, null);                    // event/company rating filters unused here
    }

    /** UI category label → EventCategory enum name for the server filter (null = no category filter). */
    static String categoryFilterValue(String label) {
        if (label == null) return null;
        return switch (label) {
            case "Concerts"  -> "CONCERT";   // seed concerts use CONCERT (enum also has the unused MUSIC)
            case "Sports"    -> "SPORTS";
            case "Theatre"   -> "THEATER";
            case "Festivals" -> "FESTIVAL";
            case "Comedy"    -> "COMEDY";
            default          -> null;        // "All categories" / unknown
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
            case "Next 7 days" -> new DateWindow(today, today.plusDays(7));
            case "Next 30 days" -> new DateWindow(today, today.plusDays(30));
            case "This month" -> new DateWindow(today, today.with(TemporalAdjusters.lastDayOfMonth()));
            case "Next 3 months" -> new DateWindow(today, today.plusMonths(3));
            default -> new DateWindow(null, null);   // "Any time"
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
        if (!REGION_ALL.equals(filterRegion))
            s.append(" in ").append(filterRegion);
        if (!DATE_ANY.equals(filterDateRange))
            s.append(" · ").append(filterDateRange.toLowerCase());
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
        Map<String, Object> row = new LinkedHashMap<>();
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

    // ---- DTO → row mapping + sort ----

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
        // Undated (TBA) events sort last in BOTH date views — so reverse the value order, not the
        // whole comparator (bySoonest.reversed() would flip nullsLast and float TBA events to the top).
        Comparator<EventRow> bySoonest = Comparator.comparing(EventRow::startsAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<EventRow> byLatest = Comparator.comparing(EventRow::startsAt,
                Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort) {
            case "Date · latest" -> byLatest;
            case "Price · low to high" -> Comparator.comparingInt(EventRow::priceCents);
            case "Price · high to low" -> Comparator.comparingInt(EventRow::priceCents).reversed();
            case "Popularity" -> Comparator.comparing(EventRow::id);
            default -> bySoonest; // soonest
        };
    }

    private static String prettyCategory(String enumName) {
        if (enumName == null) return "Other";
        return switch (enumName.toUpperCase()) {
            case "CONCERT", "MUSIC" -> "Concerts";
            case "SPORTS" -> "Sports";
            case "THEATER" -> "Theatre";
            case "FESTIVAL" -> "Festivals";
            case "COMEDY" -> "Comedy";
            default -> "Other";
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
