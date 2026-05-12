package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.events.InventoryZone;

public class InventoryZoneTest{
    
    private InventoryZone zone;


     @BeforeEach
    public void setUp() {
        zone = new InventoryZone("5", "VIP", 10, 100);
    }

    @Test
    public void GivenAhigherCapacity_WhenSetCapacity_ThenCapacityUpdated() {
        zone.setCapacity(15);
        assertEquals(15, zone.getAvailableAmount());
    }

    @Test
    public void GivenLowerCapacityThanReserved_WhenSetCapacity_ThenThrowsException() {
        zone.reserve(5);
        assertThrows(IllegalArgumentException.class, () -> zone.setCapacity(4));
    }




}
