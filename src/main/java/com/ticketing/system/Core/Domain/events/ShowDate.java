package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class ShowDate implements InvariantChecked {
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public ShowDate(LocalDateTime startTime, LocalDateTime endTime) {
        // validateTimes enforces the construction-time "must be future-dated" precondition,
        // which is NOT a perpetual invariant (a past show is still structurally valid later).
        validateTimes(startTime, endTime);

        this.startTime = startTime;
        this.endTime = endTime;
        checkInvariants();
    }

    /** Perpetual structural invariants (non-null, ordering) — distinct from the future-dated precondition. */
    @Override
    public void checkInvariants() {
        if (startTime == null) {
            throw new IllegalStateException("ShowDate invariant violated: startTime must not be null");
        }
        if (endTime == null) {
            throw new IllegalStateException("ShowDate invariant violated: endTime must not be null");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalStateException("ShowDate invariant violated: endTime must be after startTime");
        }
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        validateTimes(startTime, this.endTime);
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        validateTimes(this.startTime, endTime);
        this.endTime = endTime;
    }

    private void validateTimes(LocalDateTime startTime, LocalDateTime endTime) {
    LocalDateTime now = LocalDateTime.now();

    if (startTime.isBefore(now) || startTime.isEqual(now)) {
        throw new IllegalArgumentException("Start time must be in the future");
    }

    if (endTime.isBefore(now) || endTime.isEqual(now)) {
        throw new IllegalArgumentException("End time must be in the future");
    }

    if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
        throw new IllegalArgumentException("End time must be after start time");
    }
}
}