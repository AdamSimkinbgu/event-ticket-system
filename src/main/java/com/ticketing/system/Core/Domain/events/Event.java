
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;
import com.ticketing.system.Core.Domain.policies.purchase.AndPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.PurchaseContext;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;

public class Event implements InvariantChecked {
    private final int id;
    private final String name;
    private final Double rating;
    private final List<String> artistsNames;
    private final EventCategory category;
    private final int comapnyid;
    private EventStatus status;
    private VenueMap venueMap;
    private final List<ShowDate> showDates;
    private PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    public Event(int id, String name, Double rating, List<String> artistsNames, EventCategory category, int comapnyid,
            EventStatus status, VenueMap venueMap, List<ShowDate> showDates, PurchasePolicy PurchasePolicy,
         DiscountPolicy discountPolicy) {
        this.id = id;
        this.name = name;
        this.rating = rating;
        this.artistsNames = artistsNames;
        this.category = category;
        this.comapnyid = comapnyid;
        this.status = status;
        this.venueMap = venueMap;
        this.showDates = showDates;
         if (PurchasePolicy == null) {
        this.purchasePolicy = new NoPurchasePolicy();
    }
        this.discountPolicy = discountPolicy;
    }






    /**
     * Generic inventory reservation. The zone itself decides whether this is a
     * standing quantity or a seated selection.
     */
    public boolean reserveInventory(int zoneId, InventorySelection selection) {
        validateCanReserve(selection);
        this.venueMap.reserveInventory(zoneId, selection);
        return true;
    }

    public boolean releaseInventory(int zoneId, InventorySelection selection) {
        validateInventoryAction(selection);
        this.venueMap.releaseInventory(zoneId, selection);
        return true;
    }

    public void confirmInventorySale(int zoneId, InventorySelection selection) {
        validateInventoryAction(selection);
        this.venueMap.confirmSale(zoneId, selection);
    }









    private void validateCanReserve(InventorySelection selection) {
        validateInventoryAction(selection);

        if (status == EventStatus.CANCELED) {
            throw new IllegalStateException("Cannot reserve tickets for a canceled event");
        }

        if (status == EventStatus.SOLD_OUT) {
            throw new IllegalStateException("Cannot reserve tickets for a sold-out event");
        }

        if (status == EventStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reserve tickets for a completed event");
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


    public double calculatePriceforoneticket(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
        return discountPolicy.calculatePriceforoneticket(quantity, priceAtoneticketReservation, now);
    }







    // UC-20 — only company that owns the event can configure the venue map; must be done before going ON_SALE.
    // For simplicity, we allow configuring the entire map at once, rather than incremental updates.
    public void configureVenueMap(VenueMap venueMap, int incomingCompanyId) {
        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to configure venue map");
        }

        if (venueMap == null) {
            throw new IllegalArgumentException("Venue map cannot be null");
        }

        if (status == EventStatus.ON_SALE || status == EventStatus.SOLD_OUT || status == EventStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reconfigure venue map after event is on sale");
        }

        if (this.venueMap != null && hasReservedOrSoldInventory()) {
            throw new IllegalStateException("Cannot reconfigure venue map while tickets are reserved or sold");
        }

        this.venueMap = venueMap;
    }


    private boolean hasReservedOrSoldInventory() {
        if (venueMap == null) {
            return false;
        }
        return venueMap.getInventoryZones().stream()
                .anyMatch(zone -> zone.getReservedAmount() > 0 || zone.getSoldAmount() > 0);
    }

    





    public void updateStandingZoneCapacity(int zoneId, int newCapacity, int incomingCompanyId) {

        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to update zone capacity");
        }

        if (this.venueMap == null) {
            throw new RuntimeException("Venue map must be initialized first");
        }

        InventoryZone zone = this.venueMap.getZone(zoneId);

        if (!zone.isStanding()) {
            throw new IllegalStateException("Cannot update capacity of seated zone directly");
        }

        zone.setStandingCapacity(newCapacity);
    }









    // UC-19 / UC-32 — DRAFT/SCHEDULED -> ON_SALE when admin opens or owner publishes.
    public void transitionToOnSale() {
        if (status == EventStatus.ON_SALE) {
            return;
        }

        if (venueMap == null || venueMap.getInventoryZones().isEmpty()) {
            throw new IllegalStateException("Cannot publish event without venue map and inventory");
        }

        if (showDates.isEmpty()) {
            throw new IllegalStateException("All show dates must be present before going on sale");
        }

        checkInvariants(); // enforce all invariants before allowing sales to start

        //TODO:   <<------------------  see what more checks are needed to be added here

        if (status != EventStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled events can go on sale");
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
    public void markSoldOut() {
        if (status == EventStatus.SOLD_OUT) {
            return;
        }

        if (status != EventStatus.ON_SALE) {
            throw new IllegalStateException("Only on-sale events can be marked as sold out");
        }

        this.status = EventStatus.SOLD_OUT;
    }

    


    // UC-19 — II.3.5.2 immutability check; returns false if 'field' is frozen by sales.
    public boolean canBeEdited(String field) {  //TODO:     see what to do and if really needed        <<-----------------------------
        throw new UnsupportedOperationException("UC-19: not implemented");
    }
    








    public String getName() {
        return name;
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

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    @Override
    public void checkInvariants() {
        if (id <= 0) {
            throw new IllegalStateException("Event invariant violated: id must be positive (was " + id + ")");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Event invariant violated: name must be non-blank");
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
        if (artistsNames == null) {
            throw new IllegalStateException("Event invariant violated: artistsNames list must not be null");
        }
        if (showDates == null) {
            throw new IllegalStateException("Event invariant violated: showDates list must not be null");
        }
        // venueMap may be null in DRAFT state (UC-19) before UC-20 binds it — don't enforce non-null.
        // If present, the VenueMap's own invariants apply (cascade-check when implemented).
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

}