package com.ticketing.system.Core.Domain.events;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public record Location(
                String country,
                String city) implements InvariantChecked {

        // Compact constructor validates the canonical components before they are assigned;
        // checkInvariants() restates the same rules for the InvariantChecked contract.
        public Location {
                if (country == null || country.isBlank()) {
                        throw new IllegalStateException("Location invariant violated: country must be non-blank");
                }
                if (city == null || city.isBlank()) {
                        throw new IllegalStateException("Location invariant violated: city must be non-blank");
                }
        }

        @Override
        public void checkInvariants() {
                if (country == null || country.isBlank()) {
                        throw new IllegalStateException("Location invariant violated: country must be non-blank");
                }
                if (city == null || city.isBlank()) {
                        throw new IllegalStateException("Location invariant violated: city must be non-blank");
                }
        }

        @Override
        public String toString() {
                return city + ", " + country;
        }
}
