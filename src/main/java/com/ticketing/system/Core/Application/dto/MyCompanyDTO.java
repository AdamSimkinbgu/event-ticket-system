package com.ticketing.system.Core.Application.dto;

// A company the signed-in member belongs to, with their resolved display role
// ("Founder" / "Co-owner" / "Manager"). Feeds the owner-workspace company selector
// and name/role subtitle (V2-WIRE-OWNER-DASH). Unlike ProductionCompanyDTO this carries
// the viewer's role rather than founder/status metadata.
public record MyCompanyDTO(
    int companyId,
    String name,
    String role
) {}
