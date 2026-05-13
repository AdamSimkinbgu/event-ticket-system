package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;

// V1 stub for IPaymentGateway. Lets V1 tests run without a real payment provider.
// V2/V3 will replace with real adapters per I.3.2 (multiple gateway support).
// All bodies are stubs — owned by the team member assigned to UC-33.
public class StubPaymentGateway implements IPaymentGateway {

    @Override
    public String getId() {
        throw new UnsupportedOperationException("V1: stub identifier not set");
    }

    @Override
    public boolean verifyConnection() {
        throw new UnsupportedOperationException("V1: implement health-check stub for UC-1");
    }

    @Override
    public PaymentResultDTO charge(PaymentRequestDTO request) {
        throw new UnsupportedOperationException("UC-33: stub charge not implemented");
    }

    @Override
    public RefundResultDTO refund(int orderReceiptId, double amount) {
        throw new UnsupportedOperationException("UC-4: stub refund not implemented");
    }
}
