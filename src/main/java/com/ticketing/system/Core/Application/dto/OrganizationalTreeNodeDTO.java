package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Output of CompanyManagementService.viewOrganizationalTree() (UC-25).
// Recursive structure — each node has children appointed by it.
public record OrganizationalTreeNodeDTO(
    int userId,
    String username,
    String role,                       // "Owner" / "Manager" — value of CompanyRole as string
    boolean isFounder,
    List<String> grantedPermissions,   // Empty for Owners (they have all)
    List<OrganizationalTreeNodeDTO> appointedByThisUser
) {}
