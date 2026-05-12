package com.ticketing.system.unit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;

class CheckoutServiceTest {

    @Mock private IEventRepository mockEventRepo;
    @Mock private IProductionCompanyRepository mockCompanyRepo;
    @Mock private IUserRepository mockUserRepo;
    @Mock private AuthenticationService mockAuthService;
    @InjectMocks private EventManagementService service;

    private final String TOKEN = "valid_token";
    private final int COMPANY_ID = 10;
    private final int OWNER_ID = 1;
    private final int MANAGER_ID = 2;
    private final int EVENT_ID = 100;

    @BeforeEach
    void setup() {
        mockEventRepo = mock(IEventRepository.class);
        mockCompanyRepo = mock(IProductionCompanyRepository.class);
        mockUserRepo = mock(IUserRepository.class);
        mockAuthService = mock(AuthenticationService.class);

        service = new EventManagementService(mockEventRepo, mockCompanyRepo, null, mockAuthService);

        when(mockAuthService.validateToken(TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(TOKEN)).thenReturn(MANAGER_ID);

        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        company.validateManagerInvitation(COMPANY_ID, MANAGER_ID, OWNER_ID, List.of());
        company.acceptManagerInvitation(MANAGER_ID);

        when(mockCompanyRepo.findById(COMPANY_ID)).thenReturn(company);
    }
















    @Test @Disabled("UC-10: successful checkout — charge + issue + receipt + notify")
    void givenValidOrder_whenCheckout_thenChargedIssuedReceipted() {}

    @Test @Disabled("UC-10: II.2.8.2 atomic — payment fails → no tickets issued, locks released")
    void givenChargeFailure_whenCheckout_thenNothingPersisted() {}

    @Test @Disabled("UC-10 + UC-4: issuance fails → triggers refund pipeline")
    void givenIssuanceFailure_whenCheckout_thenRefundFires() {}

    @Test @Disabled("UC-10: II.2.8.1 expired timer rejects checkout")
    void givenExpiredOrder_whenCheckout_thenRejected() {}

    @Test @Disabled("UC-10: II.2.8.1 policy violation rejects checkout")
    void givenPolicyViolation_whenCheckout_thenRejected() {}

    @Test @Disabled("UC-33: payment gateway is called with correct amount")
    void givenSuccessfulCharge_whenCheckout_thenGatewayInvokedCorrectly() {}

    @Test @Disabled("UC-34: ticket issuer is called for issued tickets")
    void givenSuccessfulIssuance_whenCheckout_thenIssuerInvokedCorrectly() {}
}
