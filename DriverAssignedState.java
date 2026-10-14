package com.ridebooking.state;

import com.ridebooking.model.Trip;
import com.ridebooking.model.TripStatus;

public class DriverAssignedState implements TripState {
    @Override public TripStatus status() { return TripStatus.DRIVER_ASSIGNED; }

    @Override
    public void driverArrived(Trip trip) {
        trip.setState(new DriverArrivedState());
    }

    @Override
    public void cancel(Trip trip) {
        trip.setState(new CancelledState());
    }
}
