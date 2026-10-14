package com.ridebooking.service;

import com.ridebooking.exception.NoDriverAvailableException;
import com.ridebooking.exception.TripNotFoundException;
import com.ridebooking.model.Driver;
import com.ridebooking.model.Location;
import com.ridebooking.model.Rider;
import com.ridebooking.model.Trip;
import com.ridebooking.model.VehicleType;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.TripRepository;
import com.ridebooking.strategy.FareStrategy;
import com.ridebooking.strategy.MatchingStrategy;

import java.util.ArrayList;
import java.util.List;

public class RideService {
    /** Roads are longer than the straight line between two points. */
    public static final double ROAD_FACTOR = 1.3;
    private static final double AVERAGE_SPEED_KMPH = 30.0;   // used for trip duration in the fare
    private static final double ETA_SPEED_KMPH = 25.0;       // used for "driver is N min away"

    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final LocationService locationService;
    private final MatchingStrategy matchingStrategy;
    private final FareStrategy fareStrategy;
    private final double searchRadiusKm;

    public RideService(DriverRepository driverRepository,
                       TripRepository tripRepository,
                       LocationService locationService,
                       MatchingStrategy matchingStrategy,
                       FareStrategy fareStrategy,
                       double searchRadiusKm) {
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.locationService = locationService;
        this.matchingStrategy = matchingStrategy;
        this.fareStrategy = fareStrategy;
        this.searchRadiusKm = searchRadiusKm;
    }

    public void registerDriver(Driver driver) {
        driverRepository.save(driver);
    }

    /** Available drivers of this type within the search radius, best match first. */
    public List<Driver> findNearbyDrivers(Location pickup, VehicleType type) {
        List<Driver> inRange = new ArrayList<>();
        for (Driver d : driverRepository.findAvailableByType(type)) {
            if (locationService.distanceKm(d.getCurrentLocation(), pickup) <= searchRadiusKm) {
                inRange.add(d);
            }
        }
        return matchingStrategy.rank(inRange, pickup);
    }

    /** Approximate driving distance (straight line x road factor). */
    public double roadDistanceKm(Location a, Location b) {
        return locationService.distanceKm(a, b) * ROAD_FACTOR;
    }

    public double estimateFare(Location pickup, Location drop, VehicleType type) {
        double roadKm = roadDistanceKm(pickup, drop);
        double durationMinutes = roadKm / AVERAGE_SPEED_KMPH * 60.0;
        return fareStrategy.calculate(type, roadKm, durationMinutes);
    }

    /** "Driver is N minutes away", from the driver's straight-line distance to the pickup. */
    public int estimateEtaMinutes(double driverStraightKm) {
        return Math.max(1, (int) Math.round(driverStraightKm * ROAD_FACTOR / ETA_SPEED_KMPH * 60.0));
    }

    /**
     * Creates a trip and assigns a driver. tryReserve() is atomic, so even if
     * 100 riders ask at once, each driver is handed to exactly one of them.
     */
    public Trip requestRide(Rider rider, Location pickup, Location drop, VehicleType type) {
        Trip trip = new Trip(rider, pickup, drop, type);
        trip.setFare(estimateFare(pickup, drop, type));
        tripRepository.save(trip);

        for (Driver driver : findNearbyDrivers(pickup, type)) {
            if (driver.tryReserve()) {          // only one caller can win this driver
                trip.assignDriver(driver);
                return trip;
            }
            // someone else took this driver a moment ago -> try the next best
        }

        trip.cancel();
        throw new NoDriverAvailableException(
                "No " + type + " driver available within " + searchRadiusKm + " km");
    }

    public Trip driverArrived(String tripId) {
        Trip trip = getTrip(tripId);
        trip.driverArrived();
        return trip;
    }

    public Trip startTrip(String tripId) {
        Trip trip = getTrip(tripId);
        trip.start();
        return trip;
    }

    public Trip completeTrip(String tripId) {
        Trip trip = getTrip(tripId);
        trip.complete();                                  // throws if not allowed
        trip.setFare(estimateFare(trip.getPickup(), trip.getDrop(), trip.getVehicleType()));
        Driver driver = trip.getDriver();
        driver.updateLocation(trip.getDrop());            // driver is now at the drop point
        driver.release();
        return trip;
    }

    public Trip cancelTrip(String tripId) {
        Trip trip = getTrip(tripId);
        trip.cancel();                                    // throws if not allowed
        Driver driver = trip.getDriver();
        if (driver != null) {
            driver.release();
        }
        return trip;
    }

    public Trip getTrip(String tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
    }
}
