package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {
    @Test @Disabled("UC-3: browseEventCatalog returns active events from active companies")
    void givenActiveCompanies_whenBrowse_thenReturnsActiveEvents() {}

    @Test @Disabled("UC-3: events from closed companies excluded")
    void givenClosedCompany_whenBrowse_thenEventsExcluded() {}

    @Test @Disabled("UC-7: searchGlobal applies all filters (price/date/location/rating)")
    void givenFilters_whenSearchGlobal_thenFiltered() {}

    @Test @Disabled("UC-7: searchByCompany excludes the rating filter (II.2.3.2)")
    void givenCompanyScope_whenSearch_thenRatingNotApplied() {}

    @Test @Disabled("UC-8: getEventVenueMap shows free/occupied per seat")
    void givenSeatedZone_whenGetMap_thenPerSeatStatus() {}
}
