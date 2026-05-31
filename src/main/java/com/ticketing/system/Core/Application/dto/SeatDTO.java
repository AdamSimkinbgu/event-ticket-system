package com.ticketing.system.Core.Application.dto;

public record SeatDTO(
        String label,
        double x,
        double y,
        String status
) {}