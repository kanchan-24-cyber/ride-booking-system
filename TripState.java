package com.ridebooking.state;

import com.ridebooking.exception.InvalidStateTransitionException;
import com.ridebooking.model.Driver;
import com.ridebooking.model.Trip;
import com.ridebooking.model.TripStatus;

/**
 * Every action is illegal by default. Each concrete state overrides ONLY the
 * actions that are allowed from it. That is how invalid transitions get blocked.
 */
public interface TripState {

    TripStatus status();

    default void assignDriver(Trip trip, Driver driver) {
        throw new InvalidStateTransitionException(status(), "assign a driver");
    }

    default void driverArrived(Trip trip) {
        throw new InvalidStateTransitionException(status(), "mark driver arrived");
    }

    default void start(Trip trip) {
        throw new InvalidStateTransitionException(status(), "start the trip");
    }

    default void complete(Trip trip) {
        throw new InvalidStateTransitionException(status(), "complete the trip");
    }

    default void cancel(Trip trip) {
        throw new InvalidStateTransitionException(status(), "cancel the trip");
    }
}
