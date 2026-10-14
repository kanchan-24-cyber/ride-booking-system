package com.ridebooking.strategy;

import com.ridebooking.model.VehicleType;

public interface FareStrategy {
    double calculate(VehicleType vehicleType, double distanceKm, double durationMinutes);
}
