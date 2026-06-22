package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.AnnouncementRequestDTO;
import com.ticketing.system.Core.Application.dto.GuestSessionDTO;
import com.ticketing.system.Core.Application.dto.LoginDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.StartConversationRequestDTO;
import com.ticketing.system.Core.Application.dto.SubmitComplaintRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.messaging.ConversationType;
import com.ticketing.system.Core.Domain.messaging.ParticipantType;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

import java.util.List;
import java.util.Map;

/**
 * Seeds the centralized messaging aggregate (Conversation) per the
 * team's design memo: one buyer↔company inquiry, two open complaints
 * to the admin queue, and one platform-wide admin announcement.
 *
 * <p>The announcement goes out as {@code dev.admin} — already created
 * by {@code DevUserSeeder} but not held in the seed users map. This
 * seeder logs that account in itself to obtain the token.
 */
public final class DemoMessaging {

    private final AuthenticationService auth;
    private final MessagingService messaging;
    private final IUserRepository userRepository;
    private final IAdminRepository adminRepository;
    private final Map<String, SeededUser> users;
    private final Map<String, ProductionCompanyDTO> companies;

    public DemoMessaging(AuthenticationService auth,
                         MessagingService messaging,
                         IUserRepository userRepository,
                         IAdminRepository adminRepository,
                         Map<String, SeededUser> users,
                         Map<String, ProductionCompanyDTO> companies) {
        this.auth = auth;
        this.messaging = messaging;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.users = users;
        this.companies = companies;
    }

    public void seed() {
        // -- Inquiry: Avi → Live Nation Israel about Coldplay parking --
        SeededUser avi = users.get(DemoUsers.AVI_BUYER);
        ProductionCompanyDTO liveNation = companies.get(DemoCompanies.LIVE_NATION);
        messaging.startConversation(avi.token(), new StartConversationRequestDTO(
            avi.userId(), ParticipantType.MEMBER.name(),
            liveNation.companyId(), ParticipantType.COMPANY.name(),
            ConversationType.INQUIRY.name(),
            "Parking at Park HaYarkon",
            "Hi! Is there free parking near Park HaYarkon for the Coldplay show, or should I plan for a paid lot?"
        ));

        // -- Open complaint: Dana about a refund delay --
        SeededUser dana = users.get(DemoUsers.DANA_BUYER);
        messaging.submitComplaint(dana.token(), new SubmitComplaintRequestDTO(
            dana.userId(),
            "Refund delay on Beyoncé tickets",
            "I refunded my Beyoncé tickets three weeks ago and the money still hasn't come back to my card. Can you check?",
            null
        ));

        // -- Open complaint: Maya about a password-reset bug --
        SeededUser maya = users.get(DemoUsers.MAYA_BUYER);
        messaging.submitComplaint(maya.token(), new SubmitComplaintRequestDTO(
            maya.userId(),
            "Password reset email never arrives",
            "I have tried the \"forgot password\" link three times and no email has reached me. Please help.",
            null
        ));

        // -- Announcement: dev.admin → BROADCAST_MEMBERS --
        seedAdminAnnouncement();
    }

    private void seedAdminAnnouncement() {
        User adminUser = userRepository.findByUsername("dev.admin")
            .orElseThrow(() -> new IllegalStateException(
                "dev.admin not present — DevUserSeeder must run before DemoDataSeeder"));

        // dev.admin is created only as a User (admin status is a Presentation-layer routing
        // concern). Messaging gates announce() on a real domain Admin, so register one here
        // keyed by the same id the token resolves to.
        if (adminRepository.findById(adminUser.getUserId()) == null) {
            adminRepository.save(new Admin(
                adminUser.getUserId(), "dev.admin", "dev-seed-admin", false));
        }

        GuestSessionDTO guest = auth.startGuestSession();
        LoginDTO login = auth.login(new LoginRequestDTO(
            "dev.admin", DemoUsers.PASSWORD, guest.sessionId()));

        messaging.announce(login.authToken().token(), new AnnouncementRequestDTO(
            adminUser.getUserId(),
            "Reduced support hours during the holidays",
            "Heads up — customer support will run reduced hours from Dec 23 to Jan 2. Refunds may take an extra business day during this window.",
            ParticipantType.BROADCAST_MEMBERS.name(),
            List.of(),
            List.of()
        ));
    }
}
