package com.ticketing.system.Core.Application.dto;

public class CompanyRegistrationDTO {
    
    private final String name;
    private final String description;

    public CompanyRegistrationDTO(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}