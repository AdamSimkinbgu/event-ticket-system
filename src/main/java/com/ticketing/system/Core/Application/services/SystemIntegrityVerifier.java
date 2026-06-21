package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.SystemIntegrityViolationException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.users.IUserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies the platform's structural correctness against persisted state, invoked from
 * {@code SystemAdminService.initializePlatform()} after the I.1 post-conditions hold.
 *
 * <p>This javadoc is the single coverage map for <i>every</i> correctness/integrity requirement
 * in {@code requirements.md} (§1 correctness constraints, §2 integrations, §3 commercial rules,
 * and the relevant SLRs). Only <b>scannable structural state</b> is checked in this class;
 * connection rules are checked at initialization, and behavioural / transaction rules are
 * enforced at their call sites — cited below so nothing is silently dropped.
 *
 * <h3>Scanned here (structural state)</h3>
 * <ul>
 *   <li><b>§1 #6 active company has ≥1 Owner — VERIFIED.</b></li>
 *   <li><b>§1 #4 producer (owner/manager) is a registered user — VERIFIED.</b></li>
 *   <li>§1 #1 unique username, #8 ≤1 active order/buyer/event, #12 sellable ≤ inventory, and the
 *       II.4.8.3 appointment tree (single appointer, no ownership cycles) — all structural but
 *       need aggregate enumeration the V1 repos do not expose → V3 scan.</li>
 * </ul>
 *
 * <h3>Checked at initialization (connection rules)</h3>
 * <ul>
 *   <li>§1 #2 ≥1 System Admin, §2 ≥1 payment + ≥1 ticket-issuance connection — asserted by
 *       {@code SystemAdminService.verifyInitializationInvariants()}.</li>
 * </ul>
 *
 * <h3>Enforced at their call sites (behavioural / transaction rules — not scannable state)</h3>
 * <ul>
 *   <li>§1 #5 auth-on-write, #9 reservation lock, #10 atomic checkout, #11 exclusive order
 *       ownership — per-operation guards in the reservation/checkout services.</li>
 *   <li>§3 #1 charge only after the transaction confirms, #2 funds only as a result of a purchase,
 *       #3 purchase succeeds only after BOTH payment AND issuance, #4 auto-refund + notify on a
 *       broken atomic checkout — {@code CheckoutService} + the UC-4 refund pipeline.</li>
 *   <li>§3 #5 state law: mandatory receipts ({@code OrderReceipt}); anti-scalping + under-18 limits
 *       (purchase policy at checkout).</li>
 *   <li>SLR.1 no double-sold seat — per-aggregate locking; SLR.2 privacy — DTO boundary + BCrypt.</li>
 * </ul>
 *
 * <h3>Deliberately not upheld</h3>
 * <ul>
 *   <li>§1 #3 admin is a Member — the team models admins as a disjoint pool (documented override).</li>
 *   <li>§1 #7 company default policy — satisfied by construction ({@code NoPurchasePolicy} default).</li>
 * </ul>
 *
 * <p><b>V1/V2 note:</b> the in-memory repos are empty when this runs at boot (runner is
 * {@code @Order(0)}, before the dev seeders), so the scans pass trivially today; their value is
 * realized in V3 when persisted state is loaded and re-validated on startup (SLR.6 / SLR.7). The
 * logic is proven by unit tests now, and this class is the growth point for the V3 scans.
 */
@Service
@Slf4j
public class SystemIntegrityVerifier {

    private final IUserRepository userRepository;
    private final IProductionCompanyRepository companyRepository;

    public SystemIntegrityVerifier(IUserRepository userRepository,
                                   IProductionCompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    public void verify() {
        verifyActiveCompaniesHaveOwner();   // constraint #6
        verifyProducersAreUsers();          // constraint #4
        log.info("System integrity verified.");
    }

    // #6 — every active production company has at least one Owner.
    private void verifyActiveCompaniesHaveOwner() {
        for (ProductionCompany company : companyRepository.findActive()) {
            if (company.getOwnerIds().isEmpty()) {
                throw new SystemIntegrityViolationException(
                        "active company '" + company.getName() + "' has no owner");
            }
        }
    }

    // #4 — every producer (owner or manager) of an active company resolves to a known user.
    private void verifyProducersAreUsers() {
        for (ProductionCompany company : companyRepository.findActive()) {
            List<Integer> producerIds = new ArrayList<>(company.getOwnerIds());
            producerIds.addAll(company.getManagers());
            for (int userId : producerIds) {
                try {
                    userRepository.getUserById(userId);
                } catch (UserNotFoundException e) {
                    throw new SystemIntegrityViolationException(
                            "company '" + company.getName() + "' has producer " + userId
                                    + " that is not a registered user");
                }
            }
        }
    }
}
