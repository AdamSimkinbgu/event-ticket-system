package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/inquiries", layout = AdminLayout.class)
@PageTitle("Inquiries · Owner workspace")
@PermitAll
public class CompanyInquiryInboxView extends PlaceholderView {
    public CompanyInquiryInboxView() {
        super(
                "Member inquiries",
                "V2-MSG-03",
                "Bentzion Hadad",
                "Owner-side inbox for member inquiries about events / company (II.4.4). Conversation type INQUIRY, counterparty=this company. Backed by MessagingService.findInquiriesForCompany. Master/detail layout (Pattern A).");
        add(wireFilterBar("Open", "Responded", "Resolved", "All", "Event"));
        add(wireSplit(2, 1,
                wireCard("Inquiries",
                        wireGrid("Member", "Subject", "Event", "Last reply", "Status")),
                wireCard("Selected inquiry",
                        wireBox("Thread — member messages + owner replies (newest first)"),
                        wireForm("Your reply (TextArea)"),
                        wireActions("Send reply", "Mark resolved"))));
        add(wireBox("Service contract: MessagingService.respondToInquiry · MessagingService.closeConversation"));
    }
}
