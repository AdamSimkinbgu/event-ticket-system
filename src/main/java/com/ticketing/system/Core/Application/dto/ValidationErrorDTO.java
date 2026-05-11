package com.ticketing.system.Core.Application.dto;

// One field-level validation failure (e.g. JSR-303 @NotBlank, @Email).
// Aggregated into ErrorResponseDTO.fieldErrors when multiple failures occur.
public record ValidationErrorDTO(
    String field,
    String rejectedValue,
    String message
) {}
