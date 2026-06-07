package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/policies", layout = AdminLayout.class)
@PageTitle("Purchase policies · Event Ticket Platform")
@PermitAll
public class PurchasePolicyEditorView extends PlaceholderView {
    public PurchasePolicyEditorView() {
        super(
            "Purchase policies",
            "V2-PEDIT-01",
            "Abed Faour",
            "Visual tree builder for nested AND/OR purchase policies. Tier C: purchase-policy half only (discount half cancelled — V2-POL-DISC-03). Uses PolicyTreeEditor component."
        );

        add(wireSplit(2, 1,
            wireCard("Current policy",
                wirePolicyOp("AND",
                    wirePolicyLeaf("AgeAtLeast",      "buyer.age ≥ 18"),
                    wirePolicyOp("OR",
                        wirePolicyLeaf("QuantityAtLeast", "qty ≥ 1"),
                        wirePolicyLeaf("QuantityAtMost",  "qty ≤ 4")
                    )
                )
            ),
            wireColumn(
                wireCard("Scope",
                    wireBox("○  Company-level (applies to every event)"),
                    wireBox("●  Event-level (Coldplay Live · Bloomfield)")
                ),
                wireCard("Rule palette  ·  drag onto tree",
                    wirePolicyPaletteItem("AND { … }",         "All inner rules must hold"),
                    wirePolicyPaletteItem("OR { … }",          "At least one inner rule must hold"),
                    wirePolicyPaletteItem("AgeAtLeast(n)",     "Reject if buyer.age < n"),
                    wirePolicyPaletteItem("QuantityAtMost(n)", "Reject if qty > n"),
                    wirePolicyPaletteItem("QuantityAtLeast(n)","Reject if qty < n")
                ),
                wireCard("Plain English",
                    wireBox("\"Buyer must be at least 18 AND (buy between 1 and 4 tickets)\"")
                )
            )
        ));

        add(wireActions("Save policy", "Discard changes"));
        add(wireBox("Service contract: CompanyManagementService.updatePolicies(companyId, eventIdOrNull, policyTree). Validation rejects empty AND/OR groups + cycles in nesting."));
    }
}
