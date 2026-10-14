package com.ridebooking.state;

import com.ridebooking.model.TripStatus;

/** Terminal state: every action throws. */
public class CancelledState implements TripState {
    @Override public TripStatus status() { return TripStatus.CANCELLED; }
}
