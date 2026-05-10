package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;

// V1 stub for ITicketIssuer. Lets V1 tests run without a real ticket-issuance provider.
// V2/V3 will replace with real adapters per I.4.2 (multiple provider support).
// All bodies are stubs — owned by the team member assigned to UC-34.
public class StubTicketIssuer implements ITicketIssuer {

    @Override
    public String getId() {
        throw new UnsupportedOperationException("V1: stub identifier not set");
    }

    @Override
    public boolean verifyConnection() {
        throw new UnsupportedOperationException("V1: implement health-check stub for UC-1");
    }

    @Override
    public IssuanceResultDTO issue(IssuanceRequestDTO request) {
        throw new UnsupportedOperationException("UC-34: stub issuance not implemented");
    }
}
