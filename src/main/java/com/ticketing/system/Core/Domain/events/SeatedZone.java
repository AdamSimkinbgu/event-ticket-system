package com.ticketing.system.Core.Domain.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;

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
 * Adding/removing seats from a SeatedZone after tickets are issued is
 * intentionally not supported — re-create the zone if the layout changes.
 */
public class SeatedZone extends InventoryZone {

    private final Map<String, Seat> seats;
    private final Map<String, ReentrantLock> seatLocks;

    public SeatedZone(int id, String name, double price, List<Seat> initialSeats) {
        super(id, name, price);
        if (initialSeats == null) {
            throw new IllegalArgumentException("initialSeats must not be null");
        }
        this.seats = new HashMap<>();
        this.seatLocks = new HashMap<>();
        for (Seat seat : initialSeats) {
            if (this.seats.containsKey(seat.getLabel())) {
                throw new IllegalArgumentException("Duplicate seat label: " + seat.getLabel());
            }
            this.seats.put(seat.getLabel(), seat);
            this.seatLocks.put(seat.getLabel(), new ReentrantLock());
        }
    }

    /** Snapshot of the seats — modifications to the returned list don't affect the zone. */
    public List<Seat> getSeats() {
        return new ArrayList<>(seats.values());
    }

    /** Lookup a single seat by label, or {@code null} if not present. */
    public Seat getSeat(String label) {
        return seats.get(label);
    }







    
    /**
     * Reserve the given seats atomically. Either all flip to RESERVED or none do.
     *
     * @throws IllegalArgumentException if any label is unknown to this zone
     * @throws IllegalStateException    if any requested seat is not AVAILABLE
     */
    @Override
    public boolean reserve(InventorySelectionDTO selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }
        
        List<String> labels = selection.getSeatNumbers();

        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must be non-empty");
        }

        List<String> sorted = validateAndSortLabels(labels);

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
            // Commit.
            for (String label : sorted) {
                seats.get(label).setStatus(SeatStatus.RESERVED);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
        }

        return true;
    }








    /**
     * Release the given seats back to AVAILABLE. Same locking discipline as
     * {@link #reserveSeats}.
     *
     * @throws IllegalStateException if any seat is not RESERVED
     */
    @Override
    public boolean release(InventorySelectionDTO selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }

        List<String> labels = selection.getSeatNumbers();

        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must be non-empty");
        }

        List<String> sorted = validateAndSortLabels(labels);

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
                }
            }
            for (String label : sorted) {
                seats.get(label).setStatus(SeatStatus.AVAILABLE);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
        }

        return true;
    }





    /**
     * Mark seats as SOLD (e.g. after successful checkout). Same locking discipline.
     *
     * @throws IllegalStateException if any seat is not RESERVED
     */
    @Override
    public boolean confirmSale(InventorySelectionDTO selection) {
        if (!selection.isSeatedSelection()) {
            throw new IllegalArgumentException("Seated zone requires selected seat numbers");
        }

        List<String> labels = selection.getSeatNumbers();

        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("labels must be non-empty");
        }

        List<String> sorted = validateAndSortLabels(labels);

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
                }
            }
            for (String label : sorted) {
                seats.get(label).setStatus(SeatStatus.SOLD);
            }
        } finally {
            for (ReentrantLock lock : acquired) {
                lock.unlock();
            }
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

        List<String> sorted = new ArrayList<>(labels);
        Collections.sort(sorted);
        return sorted;
    }






    @Override
    public boolean checkAvailability(int quantity){
        // check if there are at least 'quantity' available seats in this zone
        int available = getAvailableAmount();
        if (available < quantity) {
            throw new IllegalStateException("Only " + available + " seats available, but " + quantity + " requested");
        }
        return true;
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
                throw new IllegalStateException("SeatedZone invariant violated: map key '" + key + "' does not match seat label '" + seat.getLabel() + "'");
            }
            seat.checkInvariants();
        }
    }
}
