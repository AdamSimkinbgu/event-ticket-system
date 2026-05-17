package com.ticketing.system.Core.Application.dto;

// Input to CompanyManagementService.appointOwner() (UC-23 step 1).
// Owners get all permissions automatically per II.4.8.1 — no permission list needed.
public record OwnerAppointmentRequestDTO(
        int companyId,
        int targetUserId) {
    public int getCompanyId() {
        return companyId;
    }

    public int getTargetUserId() {
        return targetUserId;
    }
}
