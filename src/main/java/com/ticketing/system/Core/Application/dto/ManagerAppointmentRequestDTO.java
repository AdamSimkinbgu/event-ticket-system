package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to CompanyManagementService.appointManager() (UC-24 step 1 — appoint).
// 'permissions' must be a non-empty list of Permission enum names per II.4.7.2.
public record ManagerAppointmentRequestDTO(
    int companyId,
    int targetUserId,
    List<String> permissions
) {}
