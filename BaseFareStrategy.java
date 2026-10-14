package com.ridebooking.strategy;

import com.ridebooking.model.VehicleType;

import java.util.EnumMap;
import java.util.Map;

/** fare = base + (perKm * distance) + (perMinute * duration) */
public class BaseFareStrategy implements FareStrategy {

    private record Rate(double base, double perKm, double perMinute) {}

    private final Map<VehicleType, Rate> rates = new EnumMap<>(VehicleType.class);

    public BaseFareStrategy() {
        rates.put(VehicleType.BIKE,  new Rate(20, 6, 1.0));
        rates.put(VehicleType.AUTO,  new Rate(30, 9, 1.5));
        rates.put(VehicleType.SEDAN, new Rate(50, 12, 2.0));
        rates.put(VehicleType.SUV,   new Rate(80, 18, 3.0));
    }

    @Override
    public double calculate(VehicleType vehicleType, double distanceKm, double durationMinutes) {
        Rate r = rates.get(vehicleType);
        double fare = r.base() + r.perKm() * distanceKm + r.perMinute() * durationMinutes;
        return Math.round(fare * 100.0) / 100.0;
    }
}
