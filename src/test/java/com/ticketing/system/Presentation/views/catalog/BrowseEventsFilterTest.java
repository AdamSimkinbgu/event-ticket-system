package com.ticketing.system.Presentation.views.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.time.LocalDate;

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
}
