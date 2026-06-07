package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/org-tree", layout = AdminLayout.class)
@PageTitle("Organizational tree · Admin")
@RolesAllowed("ADMIN")
public class OrganizationalTreeView extends PlaceholderView {
    public OrganizationalTreeView() {
        super(
            "Organizational tree",
            "V2-VIEW-03",
            "Naim Elijah",
            "Renders a company's manager / owner appointment tree (UC-25). Data via CompanyManagementService.viewOrganizationalTree (PR #194). Tree rendering by Bar's V2-VIEW-NW-02 OrgTreeRenderer. Closes #35, #134."
        );

        add(wireFilterBar("Company", "Include managers", "Include status badges"));

        Component alice  = wireOrgPerson("A", "Alice Cohen",  "Founder", "founder", "Founded 2024-12-15  ·  ● Active");
        Component bob    = wireOrgPerson("B", "Bob Mizrahi",  "Owner",   "owner",   "Appointed by Alice  ·  2025-01-08  ·  ● Active");
        Component carol  = wireOrgPerson("C", "Carol Levy",   "Manager", "manager", "Appointed by Bob  ·  Permissions: manage events, view sales");
        Component dave   = wireOrgPerson("D", "Dave Peretz",  "Manager", "manager", "Appointed by Bob  ·  Permissions: respond to inquiries");
        Component eve    = wireOrgPerson("E", "Eve Bar",      "Owner",   "owner",   "Appointed by Alice  ·  2025-02-14  ·  ● Active");
        Component frank  = wireOrgPerson("F", "Frank Tal",    "Manager", "manager", "Appointed by Eve  ·  Permissions: manage events, edit policies");

        Component tree = wireOrgSubtree(alice,
            wireOrgSubtree(bob,
                wireOrgSubtree(carol),
                wireOrgSubtree(dave)
            ),
            wireOrgSubtree(eve,
                wireOrgSubtree(frank)
            )
        );

        add(wireCard("Appointment hierarchy  ·  OrgTreeRenderer (V2-VIEW-NW-02, Bar)", tree));

        add(wireCard("Legend",
            wireRow(
                wireOrgPerson("●", "Founder",  "Founder", "founder", "Immutable role · created the company"),
                wireOrgPerson("●", "Owner",    "Owner",   "owner",   "Appointed by founder or another owner"),
                wireOrgPerson("●", "Manager",  "Manager", "manager", "Appointed by an owner · granular permissions")
            )
        ));

        add(wireBox("Click any person → side drawer with audit info (appointed by, when, granted permissions, recent actions). Drag-from-root motion would let admin trace the appointment path that produced this node."));
    }
}
