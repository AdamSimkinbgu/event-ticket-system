package com.ticketing.system.Presentation.presenters.catalog;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.services.CatalogService;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only presenter for {@code BrowseEventsView}. Fetches the public
 * on-sale catalog from {@link CatalogService}; the view keeps its own
 * client-side category / region / date / price filtering over the result.
 *
 * <p>Holds no Vaadin imports — the view renders, this only talks to the
 * service. A read-only query, so it returns the DTO list directly (no
 * {@code Outcome}) and degrades to an empty list on failure so the view
 * shows its empty state rather than crashing.
 */
@Slf4j
@Component
public class BrowseEventsPresenter {

    private final CatalogService catalogService;

    public BrowseEventsPresenter(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** All ON_SALE events from active companies; empty on failure. */
    public List<EventSummaryDTO> loadCatalog() {
        try {
            return catalogService.browseEventCatalog();
        } catch (RuntimeException e) {
            log.warn("Failed to load event catalog: {}", e.getMessage());
            return List.of();
        }
    }
}
