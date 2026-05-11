package com.ticketing.system.Core.Application.dto;

// Input to CompanyManagementService.registerCompany() (UC-18).
// The actor (Founder) is taken from the authenticated session, not from this DTO.
public record CompanyRegistrationDTO(
    String name,
    String description,
    String contactEmail,
    String contactPhone
) {}
