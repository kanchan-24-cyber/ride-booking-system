package com.ridebooking;

import com.ridebooking.exception.InvalidStateTransitionException;
import com.ridebooking.exception.NoDriverAvailableException;
import com.ridebooking.model.*;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.TripRepository;
import com.ridebooking.service.LocationService;
import com.ridebooking.service.RideService;
import com.ridebooking.strategy.*;

public class RideBookingApp {
    public static void main(String[] args) {
        LocationService locationService = new LocationService();
        RideService service = new RideService(
                new DriverRepository(),
                new TripRepository(),
                locationService,
                new NearestDriverStrategy(locationService),
                new BaseFareStrategy(),
                5.0);   // search radius: 5 km

        // Drivers around Indiranagar, Bengaluru
        Driver ravi  = new Driver("Ravi",  VehicleType.SEDAN, new Location(12.9784, 77.6408));
        Driver sunil = new Driver("Sunil", VehicleType.SEDAN, new Location(12.9850, 77.6500));
        Driver amit  = new Driver("Amit",  VehicleType.SEDAN, new Location(13.2000, 77.7500)); // far away
        for (Driver d : new Driver[]{ravi, sunil, amit}) {
            service.registerDriver(d);
            d.goOnline();
        }

        Location pickup = new Location(12.9719, 77.6412);   // Indiranagar
        Location drop   = new Location(12.9352, 77.6245);   // Koramangala

        System.out.println("Nearby SEDANs: " + service.findNearbyDrivers(pickup, VehicleType.SEDAN));
        System.out.printf("Estimated fare: Rs %.2f%n", service.estimateFare(pickup, drop, VehicleType.SEDAN));

        Rider rider = new Rider("Aditya");
        Trip trip = service.requestRide(rider, pickup, drop, VehicleType.SEDAN);
        System.out.println("1. Requested  -> " + trip + ", driver=" + trip.getDriver().getName());

        service.driverArrived(trip.getId());
        System.out.println("2. Arrived    -> " + trip.getStatus());

        service.startTrip(trip.getId());
        System.out.println("3. Started    -> " + trip.getStatus());

        // Try something illegal: cancelling a trip that is already running
        try {
            service.cancelTrip(trip.getId());
        } catch (InvalidStateTransitionException e) {
            System.out.println("   Blocked    -> " + e.getMessage());
        }

        service.completeTrip(trip.getId());
        System.out.printf("4. Completed  -> %s, fare=Rs %.2f%n", trip.getStatus(), trip.getFare());
        System.out.println("   Driver now -> " + trip.getDriver());

        // Second rider asks for an SUV, but we have no SUV drivers
        try {
            service.requestRide(new Rider("Neha"), pickup, drop, VehicleType.SUV);
        } catch (NoDriverAvailableException e) {
            System.out.println("5. No driver  -> " + e.getMessage());
        }
    }
}
