package com.ticketing.system.Core.Domain.events;

public record Location(
                String country,
                String city) {

        @Override
        public String toString() {
                return city + ", " + country;
        }
}
