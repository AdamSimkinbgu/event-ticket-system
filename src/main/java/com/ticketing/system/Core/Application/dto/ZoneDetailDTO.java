package com.ticketing.system.Core.Application.dto;

public record ZoneDetailDTO(
    String name,
    boolean seated,
    int rows,
    int seatsPerRow,
    int capacity,
    double price
) {}