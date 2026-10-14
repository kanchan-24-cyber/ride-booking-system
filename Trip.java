package com.ridebooking.model;

import com.ridebooking.state.RequestedState;
import com.ridebooking.state.TripState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Trip {
    private final String id;
    private final Rider rider;
    private final Location pickup;
    private final Location drop;
    private final VehicleType vehicleType;
    private final Instant requestedAt;

    private Driver driver;
    private TripState state;
    private Instant startedAt;
    private Instant completedAt;
    private double fare;

    public Trip(Rider rider, Location pickup, Location drop, VehicleType vehicleType) {
        this.id = UUID.randomUUID().toString();
        this.rider = Objects.requireNonNull(rider, "rider");
        this.pickup = Objects.requireNonNull(pickup, "pickup");
        this.drop = Objects.requireNonNull(drop, "drop");
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType");
        this.state = new RequestedState();
        this.requestedAt = Instant.now();
    }

    // ---- actions: delegated to the current state (invalid ones throw) ----
    public synchronized void assignDriver(Driver driver) { state.assignDriver(this, driver); }
    public synchronized void driverArrived()             { state.driverArrived(this); }
    public synchronized void start()                     { state.start(this); }
    public synchronized void complete()                  { state.complete(this); }
    public synchronized void cancel()                    { state.cancel(this); }

    public synchronized TripStatus getStatus() { return state.status(); }

    // ---- setters used by the state classes ----
    public synchronized void setState(TripState state) { this.state = state; }
    public synchronized void setDriver(Driver driver) { this.driver = driver; }
    public synchronized void setStartedAt(Instant t) { this.startedAt = t; }
    public synchronized void setCompletedAt(Instant t) { this.completedAt = t; }
    public synchronized void setFare(double fare) { this.fare = fare; }

    // ---- getters ----
    public String getId() { return id; }
    public Rider getRider() { return rider; }
    public Location getPickup() { return pickup; }
    public Location getDrop() { return drop; }
    public VehicleType getVehicleType() { return vehicleType; }
    public Instant getRequestedAt() { return requestedAt; }
    public synchronized Driver getDriver() { return driver; }
    public synchronized Instant getStartedAt() { return startedAt; }
    public synchronized Instant getCompletedAt() { return completedAt; }
    public synchronized double getFare() { return fare; }

    @Override
    public String toString() {
        return "Trip{" + id.substring(0, 8) + ", " + getStatus() + ", rider=" + rider.getName() + "}";
    }
}
