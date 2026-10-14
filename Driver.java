package com.ridebooking.model;

import java.util.Objects;
import java.util.UUID;

public class Driver {
    private final String id;
    private final String name;
    private final VehicleType vehicleType;
    private double rating;

    // guarded by "this"
    private Location currentLocation;
    private DriverStatus status;

    public Driver(String name, VehicleType vehicleType, Location initialLocation) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name");
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType");
        this.currentLocation = Objects.requireNonNull(initialLocation, "initialLocation");
        this.status = DriverStatus.OFFLINE;
        this.rating = 5.0;
    }

    /**
     * Atomically moves AVAILABLE -> ON_TRIP.
     * Returns true only for the single caller that wins the driver.
     * This is what prevents double-booking (stress-tested on Day 4).
     */
    public synchronized boolean tryReserve() {
        if (status != DriverStatus.AVAILABLE) {
            return false;
        }
        status = DriverStatus.ON_TRIP;
        return true;
    }

    /** Frees the driver after trip completion or cancellation. */
    public synchronized void release() {
        if (status == DriverStatus.ON_TRIP) {
            status = DriverStatus.AVAILABLE;
        }
    }

    public synchronized void goOnline() {
        if (status == DriverStatus.OFFLINE) {
            status = DriverStatus.AVAILABLE;
        }
    }

    public synchronized void goOffline() {
        if (status == DriverStatus.AVAILABLE) {
            status = DriverStatus.OFFLINE;
        }
    }

    public synchronized void updateLocation(Location location) {
        this.currentLocation = Objects.requireNonNull(location, "location");
    }

    public synchronized Location getCurrentLocation() { return currentLocation; }
    public synchronized DriverStatus getStatus() { return status; }
    public synchronized boolean isAvailable() { return status == DriverStatus.AVAILABLE; }

    public String getId() { return id; }
    public String getName() { return name; }
    public VehicleType getVehicleType() { return vehicleType; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    @Override
    public String toString() {
        return "Driver{" + name + ", " + vehicleType + ", " + getStatus() + "}";
    }
}
