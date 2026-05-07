package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;

import java.time.LocalDateTime;

public class ShowDate {
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public ShowDate(LocalDateTime startTime, LocalDateTime endTime) {
        validateTimes(startTime, endTime);

        this.startTime = startTime;
        this.endTime = endTime;
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