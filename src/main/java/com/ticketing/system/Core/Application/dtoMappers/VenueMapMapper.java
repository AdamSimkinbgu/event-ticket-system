package com.ticketing.system.Core.Application.dtoMappers;

import com.ticketing.system.Core.Application.dto.InventoryZoneDTO;
import com.ticketing.system.Core.Application.dto.LocationDTO;
import com.ticketing.system.Core.Application.dto.SeatDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.VenueMap;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VenueMapMapper {

    public VenueMapDTO venueMapToVenueMapDTO(VenueMap venueMap) {
        List<InventoryZoneDTO> inventoryZoneDTOs = venueMap.getInventoryZones()
                .stream()
                .map(this::toInventoryZoneDTO)
                .toList();
        LocationDTO location = new LocationDTO(venueMap.getLocation().country(), venueMap.getLocation().city());
        return new VenueMapDTO(venueMap.getId(), location, inventoryZoneDTOs);
    }
    



    
    private InventoryZoneDTO toInventoryZoneDTO(InventoryZone zone) {
        List<SeatDTO> seats = List.of();
        int soldAmount = 0;

        if (zone instanceof SeatedZone seatedZone) {
            seats = seatedZone.getSeats().stream()
                    .map(seat -> new SeatDTO(
                            seat.getLabel(),
                            seat.getX(),
                            seat.getY(),
                            seat.getStatus().name()
                    ))
                    .toList();

            soldAmount = seatedZone.getSoldAmount();
        }

        return new InventoryZoneDTO(
                zone.getId(),
                zone.getName(),
                zone.getZoneType().name(),
                zone.getCapacity(),
                zone.getAvailableAmount(),
                zone.getReservedAmount(),
                soldAmount,
                zone.getprice(),
                seats
        );
    }

}
