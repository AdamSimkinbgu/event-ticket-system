package com.ticketing.system.Presentation.presenters.account;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Application.services.MemberAccountService;
import com.ticketing.system.Presentation.session.AuthSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only presenter for {@code MyAccountView}. Loads the signed-in
 * member's purchase history and a best-effort eventId→name map (so the
 * tickets list can show names rather than raw ids — {@link PurchaseHistoryDTO}
 * is a thin id-based projection).
 *
 * <p>Holds no Vaadin imports. Reconstructs the {@link AuthTokenDTO} from the
 * session because {@link MemberAccountService#viewMyHistory} takes the DTO but
 * only reads its token. Degrades to an empty history when signed out / on error.
 */
@Slf4j
@Component
public class MyAccountPresenter {

    private final MemberAccountService memberAccountService;
    private final CatalogService catalogService;

    public MyAccountPresenter(MemberAccountService memberAccountService, CatalogService catalogService) {
        this.memberAccountService = memberAccountService;
        this.catalogService = catalogService;
    }

    /** The signed-in member's purchase history; empty when signed out or on failure. */
    public PurchaseHistoryDTO loadHistory() {
        AuthTokenDTO token = currentToken();
        if (token == null) {
            return new PurchaseHistoryDTO(List.of());
        }
        try {
            return memberAccountService.viewMyHistory(token);
        } catch (RuntimeException e) {
            log.warn("Failed to load purchase history: {}", e.getMessage());
            return new PurchaseHistoryDTO(List.of());
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

    private AuthTokenDTO currentToken() {
        String jwt = AuthSession.token();
        Integer userId = AuthSession.userId();
        if (jwt == null || userId == null) {
            return null;
        }
        return new AuthTokenDTO(jwt, AuthSession.expiresAtEpochMillis(), userId, AuthSession.displayName());
    }
}
