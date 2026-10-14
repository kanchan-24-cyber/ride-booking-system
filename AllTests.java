package com.ridebooking;

import com.ridebooking.exception.InvalidStateTransitionException;
import com.ridebooking.exception.NoDriverAvailableException;
import com.ridebooking.model.*;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.TripRepository;
import com.ridebooking.service.LocationService;
import com.ridebooking.service.RideService;
import com.ridebooking.strategy.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain-Java test runner (no JUnit / Maven needed).
 * Run:  java -cp out com.ridebooking.AllTests
 * In Phase 2 these can be converted to JUnit tests easily.
 */
public class AllTests {

    // ---------- tiny test harness ----------
    interface TestBody { void run() throws Exception; }

    static int passed = 0, failed = 0;

    static void test(String name, TestBody body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + t);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void expectThrows(Class<? extends Throwable> type, TestBody body) throws Exception {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
    }

    // ---------- helpers ----------
    static final Location CENTER = new Location(12.9716, 77.5946);
    static final Location DROP = new Location(12.9352, 77.6245);

    static RideService newService() {
        LocationService ls = new LocationService();
        return new RideService(new DriverRepository(), new TripRepository(), ls,
                new NearestDriverStrategy(ls), new BaseFareStrategy(), 5.0);
    }

    static Driver onlineDriver(RideService s, String name, VehicleType type, Location loc) {
        Driver d = new Driver(name, type, loc);
        s.registerDriver(d);
        d.goOnline();
        return d;
    }

    // ---------- tests ----------
    public static void main(String[] args) throws Exception {
        System.out.println("Running tests...\n");

        // -- distance --
        test("Haversine: same point is 0 km", () -> {
            check(new LocationService().distanceKm(CENTER, CENTER) < 1e-9, "should be 0");
        });
        test("Haversine: Bengaluru -> Mumbai is about 845 km", () -> {
            double d = new LocationService().distanceKm(
                    new Location(12.9716, 77.5946), new Location(19.0760, 72.8777));
            check(d > 830 && d < 860, "got " + d);
        });

        // -- Location / Driver basics --
        test("Location rejects invalid latitude", () ->
                expectThrows(IllegalArgumentException.class, () -> new Location(95, 10)));
        test("Driver: second tryReserve fails, works again after release", () -> {
            Driver d = new Driver("A", VehicleType.SEDAN, CENTER);
            d.goOnline();
            check(d.tryReserve(), "first reserve should win");
            check(!d.tryReserve(), "second reserve should fail");
            d.release();
            check(d.tryReserve(), "reserve after release should win");
        });
        test("Offline driver can not be reserved", () -> {
            Driver d = new Driver("A", VehicleType.SEDAN, CENTER);
            check(!d.tryReserve(), "offline driver reserved");
        });

        // -- matching --
        test("Nearest driver is picked", () -> {
            RideService s = newService();
            onlineDriver(s, "Far", VehicleType.SEDAN, new Location(12.9900, 77.6200));
            Driver near = onlineDriver(s, "Near", VehicleType.SEDAN, new Location(12.9720, 77.5950));
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            check(t.getDriver() == near, "wrong driver: " + t.getDriver());
        });
        test("Wrong vehicle type / offline / out of range drivers are ignored", () -> {
            RideService s = newService();
            onlineDriver(s, "Bike", VehicleType.BIKE, CENTER);
            Driver offline = new Driver("Off", VehicleType.SEDAN, CENTER);
            s.registerDriver(offline);                                  // never goes online
            onlineDriver(s, "Far", VehicleType.SEDAN, new Location(13.5, 77.9)); // ~60 km away
            expectThrows(NoDriverAvailableException.class,
                    () -> s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN));
        });
        test("HighestRatingStrategy prefers rating over distance", () -> {
            LocationService ls = new LocationService();
            RideService s = new RideService(new DriverRepository(), new TripRepository(), ls,
                    new HighestRatingStrategy(ls), new BaseFareStrategy(), 5.0);
            Driver near = onlineDriver(s, "Near", VehicleType.SEDAN, new Location(12.9720, 77.5950));
            Driver top = onlineDriver(s, "Top", VehicleType.SEDAN, new Location(12.9900, 77.6200));
            near.setRating(4.0);
            top.setRating(4.9);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            check(t.getDriver() == top, "should pick the higher rated driver");
        });

        // -- fare --
        test("Base fare: SEDAN 10 km, 20 min = 210.00", () -> {
            double fare = new BaseFareStrategy().calculate(VehicleType.SEDAN, 10, 20);
            check(fare == 210.0, "got " + fare);   // 50 + 12*10 + 2*20
        });
        test("Surge x1.5 on top of base = 315.00", () -> {
            FareStrategy surge = new SurgeFareStrategy(new BaseFareStrategy(), 1.5);
            double fare = surge.calculate(VehicleType.SEDAN, 10, 20);
            check(fare == 315.0, "got " + fare);
        });
        test("Fare uses road distance (straight line x 1.3)", () -> {
            RideService s = newService();
            double straight = new LocationService().distanceKm(CENTER, DROP);
            double roadKm = straight * RideService.ROAD_FACTOR;
            double expected = new BaseFareStrategy().calculate(VehicleType.SEDAN, roadKm, roadKm / 30.0 * 60.0);
            double actual = s.estimateFare(CENTER, DROP, VehicleType.SEDAN);
            check(Math.abs(expected - actual) < 0.01, "expected " + expected + " got " + actual);
        });
        test("ETA is at least 1 minute and grows with distance", () -> {
            RideService s = newService();
            check(s.estimateEtaMinutes(0.0) == 1, "min ETA should be 1");
            check(s.estimateEtaMinutes(5.0) > s.estimateEtaMinutes(1.0), "ETA should grow");
        });
        test("Surge multiplier below 1.0 is rejected", () ->
                expectThrows(IllegalArgumentException.class,
                        () -> new SurgeFareStrategy(new BaseFareStrategy(), 0.5)));

        // -- trip state flow --
        test("Happy path: REQUESTED -> ... -> COMPLETED, driver freed and moved to drop", () -> {
            RideService s = newService();
            Driver d = onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            check(t.getStatus() == TripStatus.DRIVER_ASSIGNED, "after request");
            check(d.getStatus() == DriverStatus.ON_TRIP, "driver should be ON_TRIP");
            s.driverArrived(t.getId());
            check(t.getStatus() == TripStatus.DRIVER_ARRIVED, "after arrived");
            s.startTrip(t.getId());
            check(t.getStatus() == TripStatus.IN_PROGRESS, "after start");
            s.completeTrip(t.getId());
            check(t.getStatus() == TripStatus.COMPLETED, "after complete");
            check(d.getStatus() == DriverStatus.AVAILABLE, "driver should be AVAILABLE");
            check(d.getCurrentLocation().equals(DROP), "driver should be at drop point");
            check(t.getFare() > 0, "fare should be set");
        });
        test("Invalid: start a trip before the driver arrived", () -> {
            RideService s = newService();
            onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            expectThrows(InvalidStateTransitionException.class, () -> s.startTrip(t.getId()));
        });
        test("Invalid: complete a trip that was never started", () -> {
            RideService s = newService();
            onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            expectThrows(InvalidStateTransitionException.class, () -> s.completeTrip(t.getId()));
        });
        test("Invalid: cancel a trip that is already IN_PROGRESS", () -> {
            RideService s = newService();
            onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            s.driverArrived(t.getId());
            s.startTrip(t.getId());
            expectThrows(InvalidStateTransitionException.class, () -> s.cancelTrip(t.getId()));
        });
        test("Invalid: nothing allowed after COMPLETED", () -> {
            RideService s = newService();
            onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            s.driverArrived(t.getId());
            s.startTrip(t.getId());
            s.completeTrip(t.getId());
            expectThrows(InvalidStateTransitionException.class, () -> s.startTrip(t.getId()));
            expectThrows(InvalidStateTransitionException.class, () -> s.cancelTrip(t.getId()));
        });
        test("Cancel after assignment frees the driver", () -> {
            RideService s = newService();
            Driver d = onlineDriver(s, "D", VehicleType.SEDAN, CENTER);
            Trip t = s.requestRide(new Rider("R"), CENTER, DROP, VehicleType.SEDAN);
            s.cancelTrip(t.getId());
            check(t.getStatus() == TripStatus.CANCELLED, "trip should be CANCELLED");
            check(d.getStatus() == DriverStatus.AVAILABLE, "driver should be AVAILABLE again");
        });

        // -- concurrency --
        test("100 riders race for 1 driver: exactly 1 wins", () -> {
            RideService s = newService();
            onlineDriver(s, "Only", VehicleType.SEDAN, CENTER);
            ConcurrentResult r = raceRiders(s, 100);
            check(r.success.get() == 1, "successes = " + r.success.get());
            check(r.noDriver.get() == 99, "noDriver = " + r.noDriver.get());
            check(r.otherErrors.get() == 0, "unexpected errors = " + r.otherErrors.get());
        });
        test("100 riders race for 10 drivers: exactly 10 win, no driver used twice", () -> {
            RideService s = newService();
            for (int i = 0; i < 10; i++) {
                onlineDriver(s, "D" + i, VehicleType.SEDAN,
                        new Location(12.9716 + i * 0.001, 77.5946));
            }
            ConcurrentResult r = raceRiders(s, 100);
            check(r.success.get() == 10, "successes = " + r.success.get());
            check(r.noDriver.get() == 90, "noDriver = " + r.noDriver.get());
            check(r.assignedDriverIds.size() == 10,
                    "distinct drivers = " + r.assignedDriverIds.size() + " (double booking!)");
        });

        System.out.println("\nResult: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------- concurrency helper ----------
    static class ConcurrentResult {
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger noDriver = new AtomicInteger();
        final AtomicInteger otherErrors = new AtomicInteger();
        final Set<String> assignedDriverIds = ConcurrentHashMap.newKeySet();
    }

    static ConcurrentResult raceRiders(RideService service, int riders) throws Exception {
        ConcurrentResult result = new ConcurrentResult();
        ExecutorService pool = Executors.newFixedThreadPool(riders);
        CountDownLatch ready = new CountDownLatch(riders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < riders; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();                       // all threads start together
                    Trip t = service.requestRide(new Rider("Rider" + n), CENTER, DROP, VehicleType.SEDAN);
                    result.success.incrementAndGet();
                    result.assignedDriverIds.add(t.getDriver().getId());
                } catch (NoDriverAvailableException e) {
                    result.noDriver.incrementAndGet();
                } catch (Throwable e) {
                    result.otherErrors.incrementAndGet();
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        return result;
    }
}
