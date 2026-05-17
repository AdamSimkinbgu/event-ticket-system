package com.ticketing.system.Core.Application.dto;

// Input to CompanyManagementService.respondToAppointment() (UC-23 / UC-24 step 2).
public record AppointmentResponseDTO(
    int companyId,
    boolean accept                       // true = ACTIVE, false = REJECTED
) {}
