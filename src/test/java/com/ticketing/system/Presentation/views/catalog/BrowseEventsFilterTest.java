package com.ticketing.system.Presentation.views.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure filter-mapping helpers of {@link BrowseEventsView} (#271) — the UI
 * category label → enum-name mapping and the date-range preset → concrete window. Lives in the
 * view's package to reach the package-private static helpers; needs no Vaadin/Spring.
 */
class BrowseEventsFilterTest {

    @Test
    void categoryFilterValue_mapsLabelsToEnumNames() {
        assertEquals("CONCERT",  BrowseEventsView.categoryFilterValue("Concerts"));
        assertEquals("SPORTS",   BrowseEventsView.categoryFilterValue("Sports"));
        assertEquals("THEATER",  BrowseEventsView.categoryFilterValue("Theatre"));
        assertEquals("FESTIVAL", BrowseEventsView.categoryFilterValue("Festivals"));
        assertEquals("COMEDY",   BrowseEventsView.categoryFilterValue("Comedy"));
    }

    @Test
    void categoryFilterValue_allOrUnknownOrNull_isNull() {
        assertNull(BrowseEventsView.categoryFilterValue("All categories"));
        assertNull(BrowseEventsView.categoryFilterValue("Nonsense"));
        assertNull(BrowseEventsView.categoryFilterValue(null));
    }

    @Test
    void dateWindow_anyTimeOrNull_isOpen() {
        assertEquals(new BrowseEventsView.DateWindow(null, null),
                BrowseEventsView.dateWindow("Any time", LocalDate.of(2026, 6, 24)));
        assertEquals(new BrowseEventsView.DateWindow(null, null),
                BrowseEventsView.dateWindow(null, LocalDate.of(2026, 6, 24)));
    }

    @Test
    void dateWindow_relativePresets_resolveAgainstToday() {
        LocalDate today = LocalDate.of(2026, 6, 24);
        assertEquals(new BrowseEventsView.DateWindow(today, today),
                BrowseEventsView.dateWindow("Today", today));
        assertEquals(new BrowseEventsView.DateWindow(today, today.plusDays(7)),
                BrowseEventsView.dateWindow("Next 7 days", today));
        assertEquals(new BrowseEventsView.DateWindow(today, today.plusDays(30)),
                BrowseEventsView.dateWindow("Next 30 days", today));
        assertEquals(new BrowseEventsView.DateWindow(today, today.plusMonths(3)),
                BrowseEventsView.dateWindow("Next 3 months", today));
        assertEquals(new BrowseEventsView.DateWindow(today, LocalDate.of(2026, 6, 30)),
                BrowseEventsView.dateWindow("This month", today));
    }

    @Test
    void dateWindow_thisWeekend_isUpcomingSaturdayToSunday() {
        LocalDate today = LocalDate.of(2026, 6, 24); // a Wednesday
        BrowseEventsView.DateWindow w = BrowseEventsView.dateWindow("This weekend", today);
        assertEquals(DayOfWeek.SATURDAY, w.from().getDayOfWeek());
        assertEquals(w.from().plusDays(1), w.to());          // Saturday → Sunday
        assertFalse(w.from().isBefore(today));               // upcoming, not in the past
    }

    @Test
    void dateWindow_customRange_isOpenBounds() {
        // "Custom range" falls through to the default case — the view reads the
        // DatePicker values directly instead of using dateWindow().
        assertEquals(new BrowseEventsView.DateWindow(null, null),
                BrowseEventsView.dateWindow("Custom range", LocalDate.of(2026, 6, 24)));
    }

    // ── locationFilter ────────────────────────────────────────────────────────

    @Test
    void locationFilter_citySelected_returnsCity() {
        assertEquals("Tel Aviv", BrowseEventsView.locationFilter("Tel Aviv", "Israel"));
    }

    @Test
    void locationFilter_citySelectedIgnoresCountry() {
        // city takes priority — country is redundant once city is known
        assertEquals("Tel Aviv", BrowseEventsView.locationFilter("Tel Aviv", "All countries"));
    }

    @Test
    void locationFilter_onlyCountrySelected_returnsCountry() {
        assertEquals("Israel", BrowseEventsView.locationFilter("All cities", "Israel"));
    }

    @Test
    void locationFilter_nothingSelected_returnsNull() {
        assertNull(BrowseEventsView.locationFilter("All cities", "All countries"));
    }

    // ── cityOptionsFor ────────────────────────────────────────────────────────

    @Test
    void cityOptionsFor_knownCountry_prependsAllCities() {
        List<String> opts = BrowseEventsView.cityOptionsFor("Israel");
        assertEquals("All cities", opts.get(0));
        assertTrue(opts.contains("Tel Aviv"));
        assertTrue(opts.contains("Jerusalem"));
        assertTrue(opts.contains("Haifa"));
    }

    @Test
    void cityOptionsFor_unknownCountry_returnsOnlyAllCities() {
        assertEquals(List.of("All cities"), BrowseEventsView.cityOptionsFor("Mars"));
    }

    @Test
    void cityOptionsFor_allCountries_returnsOnlyAllCities() {
        assertEquals(List.of("All cities"), BrowseEventsView.cityOptionsFor("All countries"));
    }

    // ── isValidCustomRange ────────────────────────────────────────────────────

    @Test
    void isValidCustomRange_fromBeforeTo_isValid() {
        assertTrue(BrowseEventsView.isValidCustomRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void isValidCustomRange_sameDay_isValid() {
        LocalDate day = LocalDate.of(2026, 6, 15);
        assertTrue(BrowseEventsView.isValidCustomRange(day, day));
    }

    @Test
    void isValidCustomRange_fromAfterTo_isInvalid() {
        assertFalse(BrowseEventsView.isValidCustomRange(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void isValidCustomRange_openBounds_areValid() {
        // a single open-ended bound (or none) is a legitimate query, not an inversion
        assertTrue(BrowseEventsView.isValidCustomRange(null, LocalDate.of(2026, 6, 1)));
        assertTrue(BrowseEventsView.isValidCustomRange(LocalDate.of(2026, 6, 1), null));
        assertTrue(BrowseEventsView.isValidCustomRange(null, null));
    }
}
