package com.ridebooking.strategy;

import com.ridebooking.model.VehicleType;

/**
 * Decorator: wraps another FareStrategy and multiplies its result.
 * e.g. new SurgeFareStrategy(new BaseFareStrategy(), 1.5)
 */
public class SurgeFareStrategy implements FareStrategy {
    private final FareStrategy base;
    private final double multiplier;

    public SurgeFareStrategy(FareStrategy base, double multiplier) {
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("Surge multiplier must be >= 1.0");
        }
        this.base = base;
        this.multiplier = multiplier;
    }

    @Override
    public double calculate(VehicleType vehicleType, double distanceKm, double durationMinutes) {
        double fare = base.calculate(vehicleType, distanceKm, durationMinutes) * multiplier;
        return Math.round(fare * 100.0) / 100.0;
    }
}
