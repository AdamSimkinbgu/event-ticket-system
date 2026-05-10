package com.ticketing.system.Core.Domain.Admin;

// Aggregate root for the Admin aggregate (System Admin, not company-side roles).
// UC-1 (I.1.4) requires the system to verify at least one Admin exists at startup,
// auto-creating a default if none.
public class Admin {

    private final int id;
    private final String username;
    private final String passwordHash;
    private final boolean isDefault;

    public Admin(int id, String username, String passwordHash, boolean isDefault) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.isDefault = isDefault;
    }

    public int getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public boolean isDefault() { return isDefault; }
}
