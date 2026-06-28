package com.ticketing.system.Core.Application.interfaces;

import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;

/**
 * Port for external ticket-issuance / barcode-generation services. Multi-provider
 * support (I.4.2) is realized via Spring DI of {@code List<ITicketIssuer>}.
 */
public interface ITicketIssuer {

    /**
     * @return the issuer's stable identifier, used for logging and routing
     */
    String getId();

    /**
     * UC-1 / I.1.3 startup verification.
     *
     * @return {@code true} if the issuance service is reachable and authenticated
     */
    boolean verifyConnection();

    /**
     * UC-34 — submit a batch of tickets for issuance and receive barcodes.
     *
     * @param request the batch of tickets to issue
     * @return the issuance result, including the generated barcodes
     * @throws com.ticketing.system.Core.Domain.exceptions.TicketIssuanceFailedException on failure
     *         (triggers the UC-4 refund pipeline)
     */
    IssuanceResultDTO issue(IssuanceRequestDTO request);
}
