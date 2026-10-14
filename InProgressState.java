package com.ridebooking.state;

import com.ridebooking.model.Trip;
import com.ridebooking.model.TripStatus;

import java.time.Instant;

/** Once the ride has started it can only be completed (no cancel). */
public class InProgressState implements TripState {
    @Override public TripStatus status() { return TripStatus.IN_PROGRESS; }

    @Override
    public void complete(Trip trip) {
        trip.setCompletedAt(Instant.now());
        trip.setState(new CompletedState());
    }
}
