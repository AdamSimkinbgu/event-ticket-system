package com.ticketing.system.Core.Application.dtoMappers;

import com.ticketing.system.Core.Application.dto.InventoryZoneDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Domain.events.VenueMap;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VenueMapMapper {

    public VenueMapDTO venueMapToVenueMapDTO(VenueMap venueMap) {
        List<InventoryZoneDTO> inventoryZoneDTOs = venueMap.getInventoryZones().stream()
            .map(zone -> new InventoryZoneDTO(zone.getId(), zone.getName(), zone.getCapacity(), zone.getReservedAmount(), zone.getprice()))
            .collect(Collectors.toList());

        return new VenueMapDTO(venueMap.getId(), inventoryZoneDTOs);
    }

}
