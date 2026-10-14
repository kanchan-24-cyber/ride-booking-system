package com.ridebooking.state;

import com.ridebooking.model.Driver;
import com.ridebooking.model.Trip;
import com.ridebooking.model.TripStatus;

public class RequestedState implements TripState {
    @Override public TripStatus status() { return TripStatus.REQUESTED; }

    @Override
    public void assignDriver(Trip trip, Driver driver) {
        trip.setDriver(driver);
        trip.setState(new DriverAssignedState());
    }

    @Override
    public void cancel(Trip trip) {
        trip.setState(new CancelledState());
    }
}
