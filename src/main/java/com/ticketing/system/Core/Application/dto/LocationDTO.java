package com.ticketing.system.Core.Application.dto;

public record LocationDTO(
                String country,
                String city) {

        @Override
        public String toString() {
                return city + ", " + country;
        }
}

