package com.ridebooking.state;

import com.ridebooking.model.Trip;
import com.ridebooking.model.TripStatus;

import java.time.Instant;

public class DriverArrivedState implements TripState {
    @Override public TripStatus status() { return TripStatus.DRIVER_ARRIVED; }

    @Override
    public void start(Trip trip) {
        trip.setStartedAt(Instant.now());
        trip.setState(new InProgressState());
    }

    @Override
    public void cancel(Trip trip) {
        trip.setState(new CancelledState());
    }
}
