package com.ridebooking.exception;

import com.ridebooking.model.TripStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(TripStatus current, String action) {
        super("Cannot " + action + " when trip is " + current);
    }
}
