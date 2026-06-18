package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.users.Permission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the four demo companies and wires up their appointments:
 * founders, one co-owner (Moshe at Shuni Productions to demonstrate the
 * co-owner accept flow), and three managers with varied permission
 * sets so {@code DevPanel}'s manager-grant grid has visible variety.
 *
 * <p>Every appointment is followed by a {@code respondToAppointment}
 * accept call so the demo state reflects an accepted role, not a
 * pending invitation.
 *
 * <p>Returns the company DTOs keyed by name; downstream seeders look
 * up the {@code companyId} they need.
 */
public final class DemoCompanies {

    public static final String LIVE_NATION = "Live Nation Israel";
    public static final String COCA_COLA   = "Coca-Cola Arena";
    public static final String HABIMA      = "Habima Theatre";
    public static final String SHUNI       = "Shuni Productions";

    private final CompanyManagementService companyService;
    private final Map<String, SeededUser> users;

    public DemoCompanies(CompanyManagementService companyService,
                         Map<String, SeededUser> users) {
        this.companyService = companyService;
        this.users = users;
    }

    public Map<String, ProductionCompanyDTO> seed() {
        Map<String, ProductionCompanyDTO> out = new LinkedHashMap<>();

        // -- Live Nation Israel — Naim founds, Faour manages (sales + CS) --
        var liveNation = register(DemoUsers.NAIM_FOUNDER, LIVE_NATION,
            "Israel's largest live-music promoter — arena tours and festivals.");
        appointManager(DemoUsers.NAIM_FOUNDER, liveNation.companyId(),
            DemoUsers.FAOUR_MANAGER, List.of(Permission.VIEW_SALES, Permission.RESPOND_TO_INQUIRIES));
        out.put(LIVE_NATION, liveNation);

        // -- Coca-Cola Arena — Moshe founds, Mohamad manages (operations) --
        var cocaCola = register(DemoUsers.MOSHE_FOUNDER, COCA_COLA,
            "Tel Aviv's flagship indoor arena — concerts, sport, and large events.");
        appointManager(DemoUsers.MOSHE_FOUNDER, cocaCola.companyId(),
            DemoUsers.MOHAMAD_MANAGER, List.of(Permission.MANAGE_INVENTORY, Permission.EDIT_POLICIES));
        out.put(COCA_COLA, cocaCola);

        // -- Habima Theatre — Bentzion founds, Ben manages (everything) --
        var habima = register(DemoUsers.BENTZION_FOUNDER, HABIMA,
            "Israel's national theatre — drama, comedy, and classical productions.");
        appointManager(DemoUsers.BENTZION_FOUNDER, habima.companyId(),
            DemoUsers.BEN_MANAGER, List.of(
                Permission.MANAGE_INVENTORY, Permission.CONFIGURE_VENUE,
                Permission.EDIT_POLICIES, Permission.VIEW_SALES,
                Permission.RESPOND_TO_INQUIRIES));
        out.put(HABIMA, habima);

        // -- Shuni Productions — Naim founds (his 2nd), Moshe as co-owner --
        var shuni = register(DemoUsers.NAIM_FOUNDER, SHUNI,
            "Boutique production company — intimate shows at Caesarea and Shuni.");
        appointOwner(DemoUsers.NAIM_FOUNDER, shuni.companyId(), DemoUsers.MOSHE_FOUNDER);
        out.put(SHUNI, shuni);

        return out;
    }

    private ProductionCompanyDTO register(String founderKey, String name, String description) {
        String token = users.get(founderKey).token();
        return companyService.registerCompany(token, new CompanyRegistrationDTO(name, description));
    }

    private void appointOwner(String founderKey, int companyId, String coOwnerKey) {
        String founderToken = users.get(founderKey).token();
        int coOwnerId       = users.get(coOwnerKey).userId();
        companyService.appointOwner(founderToken, new OwnerAppointmentRequestDTO(companyId, coOwnerId));

        String coOwnerToken = users.get(coOwnerKey).token();
        companyService.respondToAppointment(coOwnerToken, new AppointmentResponseDTO(companyId, true));
    }

    private void appointManager(String founderKey, int companyId,
                                String managerKey, List<Permission> permissions) {
        String founderToken = users.get(founderKey).token();
        int managerId       = users.get(managerKey).userId();
        companyService.appointManager(founderToken,
            new ManagerAppointmentRequestDTO(companyId, managerId, permissions));

        String managerToken = users.get(managerKey).token();
        companyService.respondToAppointment(managerToken, new AppointmentResponseDTO(companyId, true));
    }
}
