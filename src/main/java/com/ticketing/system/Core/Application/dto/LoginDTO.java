package com.ticketing.system.Core.Application.dto;

import java.util.List;

public record LoginDTO(
        AuthTokenDTO authToken,
        ActiveOrderDTO activeOrder,
        List<NotificationDTO> notifications) {
}
