package com.ticketing.system.Core.Application.dto;

import com.ticketing.system.Core.Domain.users.Permission;
import java.util.List;



public record PendingInvitationDTO(
    int    companyId,
    String companyName,
    String role,
    List<Permission> permissions,
    String inviterName
) {}
