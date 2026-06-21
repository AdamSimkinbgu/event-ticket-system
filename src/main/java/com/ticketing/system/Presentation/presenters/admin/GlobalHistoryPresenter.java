package com.ticketing.system.Presentation.presenters.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Application.services.SystemAdminService;
import com.ticketing.system.Presentation.session.AuthSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only presenter for {@code GlobalHistoryView}. Loads every purchase
 * record across the platform via {@link SystemAdminService#viewGlobalHistory}
 * (admin-gated) and a best-effort eventId→name map.
 *
 * <p>Holds no Vaadin imports. Passes an all-null {@link GlobalHistoryFiltersDTO}
 * (every field is nullable = no filter); chip filtering in the view is a
 * follow-up. Degrades to an empty list when the caller is not an admin / on error.
 */
@Slf4j
@Component
public class GlobalHistoryPresenter {

    private final SystemAdminService systemAdminService;
    private final CatalogService catalogService;

    public GlobalHistoryPresenter(SystemAdminService systemAdminService, CatalogService catalogService) {
        this.systemAdminService = systemAdminService;
        this.catalogService = catalogService;
    }

    /** Every purchase record across the platform; empty when not an admin / on failure. */
    public List<PurchaseRecordDTO> loadAllRecords() {
        String token = AuthSession.token();
        if (token == null) {
            return List.of();
        }
        try {
            GlobalHistoryFiltersDTO noFilter = new GlobalHistoryFiltersDTO(null, null, null, null, null);
            return systemAdminService.viewGlobalHistory(token, noFilter).stream()
                    .flatMap(h -> h.records().stream())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Failed to load global purchase history: {}", e.getMessage());
            return List.of();
        }
    }

    /** Best-effort eventId → name for currently on-sale events. */
    public Map<Integer, String> eventNames() {
        try {
            return catalogService.browseEventCatalog().stream()
                    .collect(Collectors.toMap(EventSummaryDTO::eventId, EventSummaryDTO::name, (a, b) -> a));
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
