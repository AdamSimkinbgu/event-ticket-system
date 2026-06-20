package com.ticketing.system.Core.Domain.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Zone with addressable, named seats. Replaces the bare counter of
 * {@link StandingZone} when the organizer wants to sell specific seats.
 *
 * <p>Each seat carries its own status (AVAILABLE / RESERVED / SOLD) and
 * its own {@link ReentrantLock}. Reserving multiple seats acquires the
 * locks in <strong>sorted label order</strong>, which prevents deadlock
 * between two concurrent buyers picking overlapping seats.
 *
 * <p>Capacity is derived from the seat map size, not a separate field.
 * Adding/removing seats from a SeatedZone after tickets are issued, so after ON_SALE turned on is
 * intentionally not supported — cannot change zones or their internals after event goes ON_SALE.
 */
public class SeatedZone extends InventoryZone {

    private final ConcurrentHashMap<String, Seat> seats;
    private final ConcurrentHashMap<String, ReentrantLock> seatLocks;
    private final ReentrantReadWriteLock layoutLock = new ReentrantReadWriteLock();



    public SeatedZone(int id, String name, double price, List<Seat> initialSeats) {
        super(id, name, price);
        if (initialSeats == null) {
            throw new IllegalArgumentException("initialSeats must not be null");
        }
        this.seats = new ConcurrentHashMap<>();
        this.seatLocks = new ConcurrentHashMap<>();
        for (Seat seat : initialSeats) {
            if (this.seats.containsKey(seat.getLabel())) {
                throw new IllegalArgumentException("Duplicate seat label: " + seat.getLabel());
            }
            this.seats.put(seat.getLabel(), seat);
            this.seatLocks.put(seat.getLabel(), new ReentrantLock());
        }
        checkInvariants();
    }

    /** Snapshot of the seats — modifications to the returned list don't affect the zone. */
    public List<Seat> getSeats() {
        return new ArrayList<>(seats.values());
    }

    
    
    
    
    public SeatStatus getSeatStatus(String label) {
        Seat seat = seats.get(label);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + label);
        }
        return seat.getStatus();
    }

    /**
     * Returns the {@link Seat} for the given label, allowing callers to inspect
     * hold identity (e.g. {@link Seat#getReservedByOrderKey()}) in Phase 3 of checkout.
     *
     * @throws IllegalArgumentException if the label is not known to this zone
     */
    public Seat getSeatByLabel(String label) {
        Seat seat = seats.get(label);
        if (seat == null) {
            throw new IllegalArgumentException("Seat not found: " + label);
        }
        return seat;
    }













    /**
     * Reserve the given seats atomically. Either all flip to RESERVED or none do.
     * Records the {@code orderKey} from the selection on each seat so that
     * Phase 3 of checkout can verify ownership before confirming the sale.
     *
     * @throws IllegalArgumentException if any label is unknown to this zone
     * @throws IllegalStateException    if any requested seat is not AVAILABLE
     */
    @Override
    public boolean reserve(InventorySelection selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }

        String orderKey = selection.getOrderKey();
        if (orderKey == null || orderKey.isBlank()) {
            throw new IllegalArgumentException(
                    "InventorySelection must carry a non-blank orderKey for seated reservations");
        }

        List<String> sorted = validateAndSortLabels(selection.getSeatNumbers());
        // lock the layout for reading while we acquire the individual seat locks. This prevents the layout from being modified (seats added/removed) while we are reserving seats.
        layoutLock.readLock().lock();
        List<ReentrantLock> acquired = new ArrayList<>();

        try {
            // Acquire all locks in sorted order to prevent deadlock with concurrent reservers.
            for (String label : sorted) {
                ReentrantLock lock = seatLocks.get(label);
                if (lock == null) {
                    throw new IllegalArgumentException("Seat not found in zone: " + label);
                }
                lock.lock();
                acquired.add(lock);
            }

            // Verify all available before any mutation — all-or-nothing.
            for (String label : sorted) {
                Seat seat = seats.get(label);
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new IllegalStateException(
                            "Seat " + label + " is not available (status: " + seat.getStatus() + ")");
                }
            }

            // Commit — stamp each seat with the holding order key.
            java.time.LocalDateTime reservedUntil = java.time.LocalDateTime.now().plusMinutes(10);
            for (String label : sorted) {
                seats.get(label).markReservedBy(orderKey, reservedUntil);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
            layoutLock.readLock().unlock();
        }

        return true;
    }
    

    /**
     * Release the given seats back to AVAILABLE. Same locking discipline as
     * {@link #reserve}. If the selection carries an {@code orderKey}, each
     * seat is verified to be held by that order before releasing.
     *
     * @throws IllegalStateException if any seat is not RESERVED (or belongs to a different order)
     */
    @Override
    public boolean release(InventorySelection selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }

        String orderKey = selection.getOrderKey();
        List<String> sorted = validateAndSortLabels(selection.getSeatNumbers());

        // lock the layout for reading while we acquire the individual seat locks. This prevents the layout from being modified (seats added/removed) while we are reserving seats.
        layoutLock.readLock().lock();
        List<ReentrantLock> acquired = new ArrayList<>();

        try {
            for (String label : sorted) {
                ReentrantLock lock = seatLocks.get(label);
                if (lock == null) {
                    throw new IllegalArgumentException("Seat not found in zone: " + label);
                }
                lock.lock();
                acquired.add(lock);
            }
            for (String label : sorted) {
                Seat seat = seats.get(label);
                if (seat.getStatus() != SeatStatus.RESERVED) {
                    throw new IllegalStateException(
                            "Seat " + label + " is not RESERVED (status: " + seat.getStatus() + ")");
                }   //TODO:  see if ok that skips ownership check when key is null seats, since those will fail the status check anyway. Otherwise, would need to check ownership before status, which is a bit weird but would be more precise in error reporting.
                String seatOwner = seat.getReservedByOrderKey();
                if (orderKey != null && !orderKey.isBlank() && !orderKey.equals(seatOwner)) {
                    throw new IllegalStateException(
                            "Seat " + label + " is reserved by order '" + seatOwner
                                    + "', not by '" + orderKey + "'");
                }
            }
            for (String label : sorted) {
                Seat seat = seats.get(label);
                seat.clearReservation();
                seat.setStatus(SeatStatus.AVAILABLE);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
            layoutLock.readLock().unlock();
        }

        return true;
    }


    /**
     * Mark seats as SOLD (e.g. after successful checkout). Same locking discipline.
     * If the selection carries an {@code orderKey}, each seat is verified to be
     * held by that order before confirming — this is the Phase 3 ownership check.
     *
     * @throws IllegalStateException if any seat is not RESERVED (or belongs to a different order)
     */
    @Override
    public boolean confirmSale(InventorySelection selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }

        String orderKey = selection.getOrderKey();
        List<String> sorted = validateAndSortLabels(selection.getSeatNumbers());

        // lock the layout for reading while we acquire the individual seat locks. This prevents the layout from being modified (seats added/removed) while we are reserving seats.
        layoutLock.readLock().lock();
        List<ReentrantLock> acquired = new ArrayList<>();

        try {
            for (String label : sorted) {
                ReentrantLock lock = seatLocks.get(label);
                if (lock == null) {
                    throw new IllegalArgumentException("Seat not found in zone: " + label);
                }
                lock.lock();
                acquired.add(lock);
            }
            for (String label : sorted) {
                Seat seat = seats.get(label);
                if (seat.getStatus() != SeatStatus.RESERVED) {
                    throw new IllegalStateException(
                            "Cannot mark SOLD — seat " + label + " is " + seat.getStatus() + " (must be RESERVED)");
                } //TODO:  see if ok that skips ownership check when key is null seats, since those will fail the status check anyway. Otherwise, would need to check ownership before status, which is a bit weird but would be more precise in error reporting.
                String seatOwner = seat.getReservedByOrderKey();
                if (orderKey != null && !orderKey.isBlank() && !orderKey.equals(seatOwner)) {
                    throw new IllegalStateException(
                            "Cannot confirm sale — seat " + label + " is reserved by order '"
                                    + seatOwner + "', not by '" + orderKey + "'");
                }
            }
            for (String label : sorted) {
                Seat seat = seats.get(label);
                seat.clearReservation();
                seat.setStatus(SeatStatus.SOLD);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
            layoutLock.readLock().unlock();
        }

        return true;
    }








    private List<String> validateAndSortLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must be non-empty");
        }

        Set<String> unique = new HashSet<>(labels);
        if (unique.size() != labels.size()) {
            throw new IllegalArgumentException("Duplicate seat labels are not allowed");
        }

        for (String label : labels) {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Seat label must be non-blank");
            }
        }

        List<String> sorted = new ArrayList<>(labels);
        Collections.sort(sorted);
        return sorted;
    }


    @Override
    public boolean checkAvailability(int quantity) {
        // check if there are at least 'quantity' available seats in this zone
        int available = getAvailableAmount();
        if (available < quantity) {
            throw new IllegalStateException("Only " + available + " seats available, but " + quantity + " requested");
        }
        return true;
    }









    
    public void addSeats(List<Seat> seatsToAdd) {
        if (seatsToAdd == null || seatsToAdd.isEmpty()) {
            throw new IllegalArgumentException("seatsToAdd must be non-empty");
        }

        layoutLock.writeLock().lock();
        try {
            Set<String> labelsToAdd = new HashSet<>();

            for (Seat seat : seatsToAdd) {
                validateSeatForInsertion(seat);

                if (!labelsToAdd.add(seat.getLabel())) {
                    throw new IllegalArgumentException("Duplicate seat label in request: " + seat.getLabel());
                }

                if (seats.containsKey(seat.getLabel())) {
                    throw new IllegalArgumentException("Seat already exists in zone: " + seat.getLabel());
                }
            }

            for (Seat seat : seatsToAdd) {
                seats.put(seat.getLabel(), seat);
                seatLocks.put(seat.getLabel(), new ReentrantLock());
            }
        } finally {
            layoutLock.writeLock().unlock();
        }
    }

    


    public void removeSeats(List<String> labelsToRemove) {
        List<String> sorted = validateAndSortLabels(labelsToRemove);

        layoutLock.writeLock().lock();
        try {
            for (String label : sorted) {
                Seat seat = seats.get(label);
                if (seat == null) {
                    throw new IllegalArgumentException("Seat not found in zone: " + label);
                }

                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    throw new IllegalStateException(
                            "Cannot remove seat " + label + " because it is "
                                    + seat.getStatus() + " and not AVAILABLE");
                }
            }

            for (String label : sorted) {
                seats.remove(label);
                seatLocks.remove(label);
            }
        } finally {
            layoutLock.writeLock().unlock();
        }
    }



    private void validateSeatForInsertion(Seat seat) {
        if (seat == null) {
            throw new IllegalArgumentException("Seat must not be null");
        }

        seat.checkInvariants();

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new IllegalArgumentException("New seat must be AVAILABLE: " + seat.getLabel());
        }
    }













    @Override
    public ZoneType getZoneType() {
        return ZoneType.SEATED;
    }

    @Override
    public int getCapacity() {
        return seats.size();
    }

    @Override
    public int getAvailableAmount() {
        return (int) seats.values().stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
    }

    @Override
    public int getReservedAmount() {
        return (int) seats.values().stream().filter(s -> s.getStatus() == SeatStatus.RESERVED).count();
    }

    @Override
    public int getSoldAmount() {
        return (int) seats.values().stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
    }

    @Override
    public void checkInvariants() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("SeatedZone invariant violated: name must be non-blank");
        }
        if (price < 0) {
            throw new IllegalStateException("SeatedZone invariant violated: price must be >= 0 (was " + price + ")");
        }
        if (seats == null) {
            throw new IllegalStateException("SeatedZone invariant violated: seats map must not be null");
        }
        if (seatLocks == null) {
            throw new IllegalStateException("SeatedZone invariant violated: seatLocks map must not be null");
        }
        // Every seat needs a matching lock entry (1:1 correspondence) and vice-versa.
        Set<String> seatKeys = seats.keySet();
        Set<String> lockKeys = seatLocks.keySet();
        if (!seatKeys.equals(lockKeys)) {
            throw new IllegalStateException("SeatedZone invariant violated: seats and seatLocks key sets disagree");
        }
        // Per-seat sanity: label matches map key and seat satisfies its own invariants.
        for (Map.Entry<String, Seat> entry : seats.entrySet()) {
            String key = entry.getKey();
            Seat seat = entry.getValue();
            if (seat == null) {
                throw new IllegalStateException("SeatedZone invariant violated: null seat for key " + key);
            }
            if (!key.equals(seat.getLabel())) {
                throw new IllegalStateException("SeatedZone invariant violated: map key '" + key
                        + "' does not match seat label '" + seat.getLabel() + "'");
            }
            seat.checkInvariants();
        }
    }
}
