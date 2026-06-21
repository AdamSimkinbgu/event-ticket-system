
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.PurchaseContext;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;

@Slf4j
public class Event implements InvariantChecked {
    private final int id;
    private String name;
    private String description;
    private final Double rating;
    private final List<String> artistsNames;
    private EventCategory category;
    private final int comapnyid;
    private EventStatus status;
    private VenueMap venueMap;
    private List<ShowDate> showDates;
    private PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    // Description-less constructor — kept for existing callers/tests. Delegates with a null
    // description (description is optional and carries no invariant).
    public Event(int id, String name, Double rating, List<String> artistsNames, EventCategory category, int comapnyid,
            EventStatus status, VenueMap venueMap, List<ShowDate> showDates, PurchasePolicy PurchasePolicy,
         DiscountPolicy discountPolicy) {
        this(id, name, null, rating, artistsNames, category, comapnyid, status, venueMap, showDates,
                PurchasePolicy, discountPolicy);
    }

    public Event(int id, String name, String description, Double rating, List<String> artistsNames,
            EventCategory category, int comapnyid, EventStatus status, VenueMap venueMap, List<ShowDate> showDates,
            PurchasePolicy PurchasePolicy, DiscountPolicy discountPolicy) {
        // Invariants are enforced by checkInvariants() at the end of construction
        // (single source of truth). Collection copies are null-safe so a null input
        // reaches checkInvariants() as a clean IllegalStateException instead of an NPE here.
        this.id = id;
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.artistsNames = artistsNames == null ? null : List.copyOf(artistsNames);
        this.category = category;
        this.comapnyid = comapnyid;
        this.status = status;
        this.venueMap = venueMap;
        this.showDates = showDates == null ? null : List.copyOf(showDates);
        // purchasePolicy defaults to NoPurchasePolicy when not supplied.
        this.purchasePolicy = PurchasePolicy == null ? new NoPurchasePolicy() : PurchasePolicy;
        // Discount Policies are not currently in the implementation plan so in the creation of the event we manually,
        // without outside intervention, set the discount to 0, not doing any discount automatically without the ability to
        // change this from the outside right now.
        this.discountPolicy = discountPolicy;
        checkInvariants();
    }






    /**
     * Generic inventory reservation. The zone itself decides whether this is a
     * standing quantity or a seated selection.
     */
    public boolean reserveInventory(int zoneId, InventorySelection selection) {
        validateCanReserve(selection);
        this.venueMap.reserveInventory(zoneId, selection);
        // When the last available place/seat is taken, the event sells out automatically.
        if (status == EventStatus.ON_SALE && !venueMap.hasAvailableInventory()) {
            markSoldOut();
        }
        return true;
    }

    public boolean releaseInventory(int zoneId, InventorySelection selection) {
        validateInventoryAction(selection);
        this.venueMap.releaseInventory(zoneId, selection);
        // Releasing inventory (manual removal, checkout rollback, or expiry sweep) re-opens
        // a sold-out event for sale again.
        if (status == EventStatus.SOLD_OUT && venueMap.hasAvailableInventory()) {
            revertToOnSale();
        }
        return true;
    }

    public void confirmInventorySale(int zoneId, InventorySelection selection) {
        validateCanConfirmSale(selection);
        this.venueMap.confirmSale(zoneId, selection);
    }













    private void validateCanReserve(InventorySelection selection) {
        validateInventoryAction(selection);
        if (status != EventStatus.ON_SALE) {
            throw new IllegalStateException("Cannot reserve tickets for an event that is not on sale");
        }
    }

    private void validateCanConfirmSale(InventorySelection selection) {
        validateInventoryAction(selection);

        // SOLD_OUT is allowed: an event that sold out while tickets were reserved must still let
        // the holders of those reservations complete their purchase (reserved -> sold).
        if (status != EventStatus.ON_SALE && status != EventStatus.SOLD_OUT) {
            throw new IllegalStateException("Cannot confirm ticket sale for an event that is not on sale");
        }
    }


    private void validateInventoryAction(InventorySelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        if (this.venueMap == null) {
            throw new IllegalStateException("Venue map must be initialized first");
        }
    }


    public boolean checkAvailability(int zoneId, int quantity) {
        return this.venueMap.checkAvailability(zoneId, quantity);
    }

    public int getId() {
        return id;
    }

    
    public double calculatePrice(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
        return discountPolicy.calculate(quantity, priceAtoneticketReservation, now);
    }
    //? These 2 functions are not in the implementation plan, so above for each event we just give discount 0, so not doing any discount.
    public double calculatePriceforoneticket(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
        return discountPolicy.calculatePriceforoneticket(quantity, priceAtoneticketReservation, now);
    }







    // UC-20 — only company that owns the event can configure the venue map; must be done before going ON_SALE.
    // this is simply a setter for the venue map.
    public void configureVenueMap(VenueMap venueMap, int incomingCompanyId) {
        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to configure venue map");
        }

        if (venueMap == null) {
            throw new IllegalArgumentException("Venue map cannot be null");
        }

        validateCanEditInventoryForCompany(incomingCompanyId);

        if (this.venueMap != null && hasReservedOrSoldInventory()) {
            throw new IllegalStateException("Cannot reconfigure venue map while tickets are reserved or sold");
        }

        this.venueMap = venueMap;

        // UC-20: binding a venue map with inventory advances a draft event to SCHEDULED.
        // Reconfiguring an already-scheduled (or later) event leaves its status untouched.
        if (status == EventStatus.DRAFT && !venueMap.getInventoryZones().isEmpty()) {
            transitionToScheduled();
        }
    }


    private boolean hasReservedOrSoldInventory() {
        if (venueMap == null) {
            return false;
        }
        return venueMap.getInventoryZones().stream()
                .anyMatch(zone -> zone.getReservedAmount() > 0 || zone.getSoldAmount() > 0);
    }








    public int addStandingZone(String zoneName, int capacity, double pricePerTicket, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        int newZoneId = venueMap.getNextZoneId();

        StandingZone standingZone = new StandingZone(
                newZoneId,
                zoneName,
                capacity,
                pricePerTicket);
        venueMap.addZone(standingZone);
        return newZoneId;
    }

    
    public int addSeatedZone(String zoneName, double pricePerTicket, List<Seat> seats, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);

        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Seated zone must contain at least one seat");
        }
        int newZoneId = venueMap.getNextZoneId();

        SeatedZone seatedZone = new SeatedZone(
                newZoneId,
                zoneName,
                pricePerTicket,
                seats);
        venueMap.addZone(seatedZone);
        return newZoneId;
    }

    
    public void removeInventoryZone(int zoneId, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        venueMap.removeZone(zoneId);
    }

    










    public void addPlacesToStandingZone(int zoneId, int amountToAdd, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        this.getVenueMapOrThrow().addPlacesToStandingZone(zoneId, amountToAdd);
    }

    public void removePlacesFromStandingZone(int zoneId, int amountToRemove, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        this.getVenueMapOrThrow().removePlacesFromStandingZone(zoneId, amountToRemove);
    }



    public void addSeatsToSeatedZone(int zoneId, List<Seat> seatsToAdd, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        this.getVenueMapOrThrow().addSeatsToSeatedZone(zoneId, seatsToAdd);
    }

    public void removeSeatsFromSeatedZone(int zoneId, List<String> seatLabelsToRemove, int incomingCompanyId) {
        validateCanEditExistingVenueInventory(incomingCompanyId);
        this.getVenueMapOrThrow().removeSeatsFromSeatedZone(zoneId, seatLabelsToRemove);
    }




    




    private void validateCanEditExistingVenueInventory(int incomingCompanyId) {
        validateCanEditInventoryForCompany(incomingCompanyId);
        if (this.venueMap == null) {
            throw new RuntimeException("Venue map must be initialized first");
        }
    }

    private void validateCanEditInventoryForCompany(int incomingCompanyId) {
        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to edit event inventory, only company that owns the event can edit inventory");
        }
        if (status != EventStatus.DRAFT && status != EventStatus.SCHEDULED) {
            throw new IllegalStateException("Inventory can only be edited while event is DRAFT or SCHEDULED");
        }
    }











    // UC-20 — DRAFT -> SCHEDULED once a venue map with inventory is bound (tickets pre-generated).
    // Auto-fired from configureVenueMap; safe to call directly (idempotent when already SCHEDULED).
    public void transitionToScheduled() {
        if (status == EventStatus.SCHEDULED) {
            return;
        }

        if (venueMap == null || venueMap.getInventoryZones().isEmpty()) {
            throw new InvalidStateTransitionException(
                    "Event cannot be scheduled without a venue map and at least one inventory zone");
        }

        if (status != EventStatus.DRAFT) {
            throw new InvalidStateTransitionException("Event", status.name(), EventStatus.SCHEDULED.name());
        }

        this.status = EventStatus.SCHEDULED;
    }


    // UC-19 / UC-32 — SCHEDULED -> ON_SALE when admin opens or owner publishes.
    public void transitionToOnSale() {
        if (status == EventStatus.ON_SALE) {
            return;
        }

        if (venueMap == null || venueMap.getInventoryZones().isEmpty()) {
            throw new InvalidStateTransitionException("Cannot publish event without venue map and inventory");
        }

        if (showDates.isEmpty()) {
            throw new InvalidStateTransitionException("All show dates must be present before going on sale");
        }

        checkInvariants(); // enforce all invariants before allowing sales to start

        if (status != EventStatus.SCHEDULED) {
            throw new InvalidStateTransitionException("Event", status.name(), EventStatus.ON_SALE.name());
        }

        this.status = EventStatus.ON_SALE;
    }




    // UC-19 — soft cancel; fires EventCancelled event for UC-4.
    public void transitionToCanceled(String reason) {
        if (status == EventStatus.CANCELED) {
            return;
        }

        if (status == EventStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed event");
        }

        this.status = EventStatus.CANCELED;
    }
    



    // ON_SALE -> COMPLETED after the last show date.
    public void transitionToCompleted() {
        if (status == EventStatus.COMPLETED) {
            return;
        }

        if (status != EventStatus.ON_SALE) {
            throw new IllegalStateException("Only on-sale events can be marked as completed");
        }

        this.status = EventStatus.COMPLETED;
    }

    


    // ON_SALE -> SOLD_OUT when no AVAILABLE tickets remain.
    // Auto-fired from reserveInventory; safe to call directly (idempotent when already SOLD_OUT).
    public void markSoldOut() {
        if (status == EventStatus.SOLD_OUT) {
            return;
        }

        if (status != EventStatus.ON_SALE) {
            throw new InvalidStateTransitionException("Event", status.name(), EventStatus.SOLD_OUT.name());
        }

        this.status = EventStatus.SOLD_OUT;
    }


    // SOLD_OUT -> ON_SALE when availability reappears (a reservation is released).
    // Auto-fired from releaseInventory; safe to call directly (idempotent when already ON_SALE).
    public void revertToOnSale() {
        if (status == EventStatus.ON_SALE) {
            return;
        }

        if (status != EventStatus.SOLD_OUT) {
            throw new InvalidStateTransitionException("Event", status.name(), EventStatus.ON_SALE.name());
        }

        this.status = EventStatus.ON_SALE;
    }

    







    // UC-19 — partial update of mutable metadata. Only allowed in DRAFT or SCHEDULED state.
    // Caller must hold the event lock (IEventRepository.lockForUpdate) before calling this method —
    // the mutated fields are non-final, so concurrent reads depend on the service's lock discipline.
    // Each argument is null-coalesced: null means "leave this field alone". The service is
    // responsible for converting LocationDTO/ShowDateDTO into the domain Location/ShowDate passed here.
    public void editDetails(String newName, String newDescription, EventCategory newCategory,
            Location newLocation, List<ShowDate> newShowDates) {
        if (status != EventStatus.DRAFT && status != EventStatus.SCHEDULED) {
            throw new IllegalStateException("Event details can only be edited while in DRAFT or SCHEDULED state");
        }
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
        if (newDescription != null) {
            this.description = newDescription;
        }
        if (newCategory != null) {
            this.category = newCategory;
        }
        if (newLocation != null && this.venueMap != null) {
            this.venueMap.setLocation(newLocation);
        }
        if (newShowDates != null) {
            // null means "leave the schedule alone"; an explicitly empty list is rejected
            // rather than silently ignored, since an event must always have >=1 show date.
            if (newShowDates.isEmpty()) {
                throw new IllegalArgumentException("showDates cannot be empty; an event must have at least one show date");
            }
            this.showDates = List.copyOf(newShowDates);
        }
        checkInvariants();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getRating() {
        return rating;
    }

    public int getCompanyId() {
        return comapnyid;
    }

    public EventStatus getStatus() {
        return status;
    }

    public EventCategory getCategory() {
        return category;
    }

    public List<String> getArtistsNames() {
        // create a new list to prevent external modification of the internal artistsNames list
        return List.copyOf(artistsNames);
    }

    public List<ShowDate> getShowDates() {
        return List.copyOf(showDates);
    }

    public VenueMap getVenueMap() {
        return venueMap;
    }

    // private getter
    private VenueMap getVenueMapOrThrow() {
        if (this.venueMap == null) {
            throw new IllegalStateException("Venue map must be initialized first");
        }
        return this.venueMap;
    }

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }





    public void setPurchasePolicy(PurchasePolicy purchasePolicy) {
        if (purchasePolicy == null) {
            throw new IllegalArgumentException("Purchase policy cannot be null");
        }

        this.purchasePolicy = purchasePolicy;
    }

    public void validatePurchasePolicy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        if (purchasePolicy == null) {
            purchasePolicy = new NoPurchasePolicy();
        }

        if (!purchasePolicy.isSatisfiedBy(context)) {
            throw new IllegalStateException(purchasePolicy.getFailureMessage());
        }
    }











    @Override
    public void checkInvariants() {
        if (id <= 0) {
            throw new IllegalStateException("Event invariant violated: id must be positive (was " + id + ")");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Event invariant violated: name must be non-blank");
        }
        if (rating != null && (rating < 0 || rating > 5)) {
            throw new IllegalStateException(
                    "Event invariant violated: rating must be between 0 and 5 (was " + rating + ")");
        }
        if (comapnyid <= 0) {
            throw new IllegalStateException(
                    "Event invariant violated: companyId must be positive (was " + comapnyid + ")");
        }
        if (status == null) {
            throw new IllegalStateException("Event invariant violated: status must not be null");
        }
        if (category == null) {
            throw new IllegalStateException("Event invariant violated: category must not be null");
        }
        if (artistsNames == null || artistsNames.isEmpty()) {
            throw new IllegalStateException("Event invariant violated: artistsNames list must be non-empty");
        }
        if (showDates == null || showDates.isEmpty()) {
            throw new IllegalStateException("Event invariant violated: showDates list must be non-empty");
        }
        if (purchasePolicy == null) {
            throw new IllegalStateException("Event invariant violated: purchasePolicy must not be null");
        }
        if (discountPolicy == null) {
            throw new IllegalStateException("Event invariant violated: discountPolicy must not be null");
        }
        // venueMap may be null in DRAFT state (UC-19) before UC-20 binds it — don't enforce non-null.
        // If present, the VenueMap's own invariants apply (cascade-check when implemented).
    }



    

}