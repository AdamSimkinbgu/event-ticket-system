package com.ticketing.system.Core.Application.dto;

// Owner-workspace dashboard counters for a single company (V2-WIRE-OWNER-DASH).
// revenue30d / ticketsSold30d are the company's share of non-refunded receipts in the
// trailing 30 days; activeEvents counts ON_SALE events; openInquiries counts unresolved
// INQUIRY conversations where the company is the counterparty.
public record CompanyDashboardDTO(
    int activeEvents,
    int ticketsSold30d,
    double revenue30d,
    int openInquiries
) {}
