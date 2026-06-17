package com.ticketing.system.Presentation.dev;

import com.ticketing.system.Core.Application.dto.GuestSessionDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Boot-time seed for the {@code dev} profile: registers two known users
 * the {@link DevPanel} can sign in as via the real
 * {@link AuthenticationService} login path.
 *
 * <p>Idempotent — if the users already exist (e.g., a JPA-backed dev
 * environment that persists across restarts) the seeder logs and moves
 * on. With the current Memory* repositories the users always need
 * (re-)creating on every boot.
 *
 * <p>{@code dev.admin} is intentionally added to
 * {@link com.ticketing.system.Presentation.session.AuthSession#ADMIN_USERNAMES}
 * so a login as that user routes to the admin shell. Production is
 * unaffected: this bean only loads under {@code @Profile("dev")} and
 * {@code RegisterView} refuses to register admin-pool names from the
 * public form, so prod users can never shadow the dev seed.
 */
@Component
@Profile("dev")
@Order(1)
@Slf4j
public class DevUserSeeder implements ApplicationRunner {

    static final String MEMBER_USERNAME = "dev.member";
    static final String MEMBER_EMAIL    = "dev.member@dev.test";
    static final String ADMIN_USERNAME  = "dev.admin";
    static final String ADMIN_EMAIL     = "dev.admin@dev.test";
    static final String SHARED_PASSWORD = "password123";
    static final int    SEED_AGE        = 25;

    private final AuthenticationService authenticationService;

    public DevUserSeeder(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(MEMBER_USERNAME, MEMBER_EMAIL);
        seed(ADMIN_USERNAME, ADMIN_EMAIL);
    }

    private void seed(String username, String email) {
        try {
            GuestSessionDTO guest = authenticationService.startGuestSession();
            authenticationService.register(new RegisterRequestDTO(
                username, email, SHARED_PASSWORD, guest.sessionId(), SEED_AGE
            ));
            log.info("dev user seeded username={}", username);
        } catch (DuplicateUsernameException | DuplicateEmailException existing) {
            log.debug("dev user already present username={}", username);
        } catch (RuntimeException e) {
            log.warn("dev user seed failed username={} reason={}", username, e.getMessage());
        }
    }
}
