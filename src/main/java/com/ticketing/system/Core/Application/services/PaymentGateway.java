package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.PaymentResultDTO;

public interface PaymentGateway {
   boolean Paying( double price );
   PaymentResultDTO refund(int userId, double amount, String reason);
 
}
