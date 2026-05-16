package com.ticketing.system.Core.Application.dtoMappers;

import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.ShowDateDTO;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;

public class EventMapper {


    // HELPER METHOD to convert Event --> EventSummaryDTO; used in both search methods above.
    public EventSummaryDTO convertEventToEventSummaryDTO(Event event, IProductionCompanyRepository productionCompanyRepository) {
        double minPrice = (event.getVenueMap() != null && !event.getVenueMap().getInventoryZones().isEmpty())
                ? event.getVenueMap().getInventoryZones().stream().mapToInt(z -> z.getprice()).min().getAsInt()
                : 0;
        double maxPrice = (event.getVenueMap() != null && !event.getVenueMap().getInventoryZones().isEmpty())
                ? event.getVenueMap().getInventoryZones().stream().mapToInt(z -> z.getprice()).max().getAsInt()
                : 0;
        return new EventSummaryDTO(
                event.getId(),
                event.getName(),
                event.getStatus().toString(),
                event.getRating(),
                productionCompanyRepository.getCompanyById(event.getCompanyId()).getName(),
                event.getCategory().toString(),
                event.getVenueMap().getLocation().toString(),
                event.getShowDates().stream().map(sd -> new ShowDateDTO(sd.getStartTime(), sd.getEndTime())).toList(),
                minPrice,
                maxPrice,
                event.getStatus() == EventStatus.SOLD_OUT);
    }


}
