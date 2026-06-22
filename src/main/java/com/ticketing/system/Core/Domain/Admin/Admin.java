package com.ticketing.system.Core.Domain.Admin;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

// Aggregate root for the Admin aggregate (System Admin, not company-side roles).
// UC-1 (I.1.4) requires the system to verify at least one Admin exists at startup,
// auto-creating a default if none.
public class Admin implements InvariantChecked {

    private final int id;
    private final String username;
    private final String passwordHash;
    private final boolean isDefault;

    public Admin(int id, String username, String passwordHash, boolean isDefault) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.isDefault = isDefault;
        checkInvariants();
    }

    public int getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public boolean isDefault() { return isDefault; }

    @Override
    public void checkInvariants() {
        if (id <= 0) {
            throw new IllegalStateException("Admin invariant violated: id must be positive (was " + id + ")");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Admin invariant violated: username must be non-blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalStateException("Admin invariant violated: passwordHash must be non-blank");
        }
    }
}
