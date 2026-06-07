package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/complaints", layout = AdminLayout.class)
@PageTitle("Complaint queue · Admin")
@RolesAllowed("ADMIN")
public class AdminComplaintQueueView extends PlaceholderView {
    public AdminComplaintQueueView() {
        super(
            "Complaint queue",
            "V2-VIEW-NW-03",
            "Bar (@BarMiyara)",
            "Admin handles complaints submitted by members via V2-MSG-02. Uses MessagingService.findByType(COMPLAINT). Master/detail layout (Pattern A). Closes #140."
        );
        add(wireFilterBar("Open", "Responded", "Resolved", "All"));
        add(wireSplit(2, 1,
            wireCard("Complaints",
                wireGrid("Member", "Subject", "Last reply", "Status")
            ),
            wireCard("Selected complaint",
                wireBox("Thread (member messages + admin replies)"),
                wireForm("Your reply (TextArea)"),
                wireActions("Send reply", "Mark resolved")
            )
        ));
    }
}
