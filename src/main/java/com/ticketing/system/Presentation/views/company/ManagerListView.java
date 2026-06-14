package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.MockSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/managers", layout = WorkspaceLayout.class)
@PageTitle("Managers · TicketHub")
@PermitAll
@RequireCapability(Capability.APPOINT_MANAGER)
public class ManagerListView extends LkPage {

    private final CompanyManagementService companyService;

    public ManagerListView(CompanyManagementService companyService) {
        this.companyService = companyService;
        title("Managers");
        subtitle("Roster and permissions for your production company.");
        actions(new LkBtn("Invite manager")
            .variant(LkBtn.Variant.primary)
            .icon(new LkIcon("plus", 15))
            .onClick(e -> UI.getCurrent().navigate(ManagerInvitationView.class)));

        add(Lk.h2("Active managers"));
        add(buildActiveCard());
        add(Lk.h2("Pending invitations"));
        add(buildPendingCard());
    }

    private Component buildActiveCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Name",        "name")
            .col("Role",        "role")
            .col("Permissions", "perms")
            .col("Status",      "status")
            .col("",            "act", LkGrid.Align.RIGHT);

        activeRow(grid, "Carol Levy",  1,  "Manager", "Manage events · Inquiries");
        activeRow(grid, "David Cohen", 2,  "Manager", "View sales · Edit policies");
        activeRow(grid, "Rina Shapiro", 3, "Manager", "Manage events");
        grid.build();
        card.add(grid);
        return card;
    }

    private void activeRow(LkGrid grid, String name, int userId, String role, String perms) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span n = new Span();
        n.getElement().setProperty("innerHTML", "<b>" + name + "</b>");
        row.put("name", n);
        row.put("role", new LkBadge(role, LkBadge.Tone.success).small());
        Span p = Lk.muted(perms);
        p.getStyle().set("font-size", "13px");
        row.put("perms", p);
        row.put("status", new LkStatusDot(LkStatusDot.Tone.ok, "Active"));
        LkRow actions = new LkRow().gap(4).noWrap();
        if (Capabilities.has(Capability.EDIT_MANAGER_PERMISSIONS)) {
            LkIconBtn edit = new LkIconBtn(new LkIcon("edit", 15), "Edit permissions");
            edit.addClickListener(e -> showEditPermissionsDialog(name, userId));            actions.add(edit);
        }
        if (Capabilities.has(Capability.REVOKE_MANAGER)) {
            LkIconBtn revoke = new LkIconBtn(new LkIcon("trash", 15), "Revoke");
            revoke.addClickListener(e -> showRevokeDialog(name, userId)); // was: Toasts.warn(...)
            actions.add(revoke);
        }
        row.put("act", actions);
        grid.row(row);
    }

        private void showRevokeDialog(String name, int userId) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Revoke manager access");
        d.setWidth("420px");
        d.setMaxWidth("92vw");

        Paragraph msg = new Paragraph(
            "Remove " + name + " as a manager? " +
            "They will lose all company access immediately and cannot undo this themselves."
        );
        d.add(msg);

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button confirm = new Button("Revoke access", e -> {
            String token = MockAuth.token();
            if (token == null) {
                Toasts.failure("Session token missing — please log in again.");
                d.close();
                return;
            }
            try {
                String companyIdStr = MockSession.currentCompanyId();
                int companyId = Integer.parseInt(companyIdStr);
                companyService.RevokeAppointment(token, new AppointmentRevokeDTO(companyId, userId));
                Toasts.success(name + "'s manager access has been revoked.");
                d.close();
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Toasts.failure("Could not revoke access: " + ex.getMessage());
                d.close();
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

        d.getFooter().add(cancel, confirm);
        d.open();
    }

    private void showEditPermissionsDialog(String name, int userId) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Edit permissions — " + name);
        d.setWidth("420px");
        d.setMaxWidth("92vw");

        LinkedHashMap<Permission, String> labels = new LinkedHashMap<>();
        labels.put(Permission.MANAGE_INVENTORY,     "Manage inventory");
        labels.put(Permission.CONFIGURE_VENUE,      "Configure venue");
        labels.put(Permission.EDIT_POLICIES,        "Edit policies");
        labels.put(Permission.VIEW_SALES,           "View sales");
        labels.put(Permission.RESPOND_TO_INQUIRIES, "Respond to inquiries");

        VerticalLayout checks = new VerticalLayout();
        checks.setPadding(false);
        checks.setSpacing(false);
        Map<Checkbox, Permission> boxMap = new LinkedHashMap<>();
        labels.forEach((perm, label) -> {
            Checkbox cb = new Checkbox(label);
            cb.setValue(true);
            boxMap.put(cb, perm);
            checks.add(cb);
        });
        d.add(checks);

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button save = new Button("Save", e -> {
            String token = MockAuth.token();
            if (token == null) {
                Toasts.failure("Session token missing — please log in again.");
                d.close();
                return;
            }
            List<Permission> selected = boxMap.entrySet().stream()
            .filter(entry -> entry.getKey().getValue())
            .map(Map.Entry::getValue)
            .toList();

            if (selected.isEmpty()) {
                Toasts.warn("A manager must have at least one permission.");
                return;
            }
            try {
                int companyId = Integer.parseInt(MockSession.currentCompanyId());
                companyService.editManagerPermissions(
                    token, new PermissionEditDTO(companyId, userId, selected));
                Toasts.success(name + "'s permissions have been updated.");
                d.close();
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Toasts.failure("Could not update permissions: " + ex.getMessage());
                d.close();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        d.getFooter().add(cancel, save);
        d.open();
    }


    private Component buildPendingCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Invitee", "invitee")
            .col("Role",    "role")
            .col("Sent",    "sent")
            .col("Status",  "status");

        pendingRow(grid, "yossi.mizrahi", "Manager", "3 days ago");
        pendingRow(grid, "lior.adler",    "Manager", "1 week ago");
        grid.build();
        card.add(grid);
        return card;
    }

    private void pendingRow(LkGrid grid, String invitee, String role, String sent) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span inv = Lk.mono(invitee);
        row.put("invitee", inv);
        row.put("role", role);
        row.put("sent", sent);
        row.put("status", new LkStatusDot(LkStatusDot.Tone.warn, "Pending"));
        grid.row(row);
    }
}
