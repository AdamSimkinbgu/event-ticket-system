package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.GuestSessionDTO;
import com.ticketing.system.Core.Application.dto.LoginDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.users.IUserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds the named demo users (3 founders, 3 managers, 4 buyers) on top
 * of the two personas {@code DevUserSeeder} already creates.
 *
 * <p>Each user is registered via {@link AuthenticationService#register}
 * and immediately signed in via {@link AuthenticationService#login} so
 * downstream seeders have a JWT to make service calls on their behalf.
 * The whole pipeline runs under {@code @Profile("dev")}, never in
 * production.
 *
 * <p>Returns a {@link LinkedHashMap} keyed by username so seed order is
 * deterministic — useful when inspecting test fixtures.
 */
public final class DemoUsers {

    public static final String PASSWORD = "password123";

    public static final String NAIM_FOUNDER     = "naim.founder";
    public static final String MOSHE_FOUNDER    = "moshe.founder";
    public static final String BENTZION_FOUNDER = "bentzion.founder";
    public static final String FAOUR_MANAGER    = "faour.manager";
    public static final String MOHAMAD_MANAGER  = "mohamad.manager";
    public static final String BEN_MANAGER      = "ben.manager";
    public static final String AVI_BUYER        = "avi.avocado";
    public static final String DANA_BUYER       = "dana.dabkeh";
    public static final String IDO_BUYER        = "ido.idealist";
    public static final String MAYA_BUYER       = "maya.manyana";

    private final AuthenticationService auth;
    private final IUserRepository userRepository;

    public DemoUsers(AuthenticationService auth, IUserRepository userRepository) {
        this.auth = auth;
        this.userRepository = userRepository;
    }

    public Map<String, SeededUser> seed() {
        Map<String, SeededUser> out = new LinkedHashMap<>();

        // Founders — each will own a company in DemoCompanies.
        registerAndLogin(out, NAIM_FOUNDER,     "naim@demo.test",     32);
        registerAndLogin(out, MOSHE_FOUNDER,    "moshe@demo.test",    38);
        registerAndLogin(out, BENTZION_FOUNDER, "bentzion@demo.test", 41);

        // Managers — appointed in DemoCompanies with varied permission sets.
        registerAndLogin(out, FAOUR_MANAGER,    "faour@demo.test",    29);
        registerAndLogin(out, MOHAMAD_MANAGER,  "mohamad@demo.test",  33);
        registerAndLogin(out, BEN_MANAGER,      "ben@demo.test",      27);

        // Buyers — reservations + past orders in DemoOrders.
        registerAndLogin(out, AVI_BUYER,        "avi.avocado@demo.test",  26);
        registerAndLogin(out, DANA_BUYER,       "dana.dabkeh@demo.test",  31);
        registerAndLogin(out, IDO_BUYER,        "ido.idealist@demo.test", 24);
        registerAndLogin(out, MAYA_BUYER,       "maya.manyana@demo.test", 28);

        return out;
    }

    private void registerAndLogin(Map<String, SeededUser> out,
                                  String username, String email, int age) {
        GuestSessionDTO guest = auth.startGuestSession();
        auth.register(new RegisterRequestDTO(username, email, PASSWORD, guest.sessionId(), age));
        // The same guest sessionId is promoted in place by login per D10a.
        LoginDTO login = auth.login(new LoginRequestDTO(username, PASSWORD, guest.sessionId()));
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException(
                "seed user vanished between register and lookup: " + username));
        out.put(username, new SeededUser(user, login.authToken().token()));
    }
}
