package com.ridebooking.web;

import com.ridebooking.exception.InvalidStateTransitionException;
import com.ridebooking.exception.NoDriverAvailableException;
import com.ridebooking.exception.TripNotFoundException;
import com.ridebooking.model.Driver;
import com.ridebooking.model.Location;
import com.ridebooking.model.Rider;
import com.ridebooking.model.Trip;
import com.ridebooking.model.VehicleType;
import com.ridebooking.repository.DriverRepository;
import com.ridebooking.repository.TripRepository;
import com.ridebooking.service.LocationService;
import com.ridebooking.service.RideService;
import com.ridebooking.strategy.BaseFareStrategy;
import com.ridebooking.strategy.FareStrategy;
import com.ridebooking.strategy.NearestDriverStrategy;
import com.ridebooking.strategy.SurgeFareStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live web version. Uses only the JDK's built-in HttpServer.
 *   java -cp out com.ridebooking.web.WebServer      ->  http://localhost:8080
 * Env vars: PORT (default 8080), SURGE (fare multiplier, default 1.0), WEB_DIR (default "web").
 */
public class WebServer {
    private static final double RADIUS_KM = 5.0;

    private final RideService rideService;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final LocationService locationService;
    private final Path webDir;
    private final double surge;
    private final AtomicInteger driverCounter = new AtomicInteger();

    public WebServer(Path webDir, double surge) {
        this.webDir = webDir.toAbsolutePath().normalize();
        this.surge = surge;
        this.locationService = new LocationService();
        this.driverRepository = new DriverRepository();
        this.tripRepository = new TripRepository();
        FareStrategy fare = new BaseFareStrategy();
        if (surge > 1.0) {
            fare = new SurgeFareStrategy(fare, surge);
        }
        this.rideService = new RideService(driverRepository, tripRepository, locationService,
                new NearestDriverStrategy(locationService), fare, RADIUS_KM);
        seedDrivers();
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        double surge = Double.parseDouble(System.getenv().getOrDefault("SURGE", "1.0"));
        Path webDir = Paths.get(System.getenv().getOrDefault("WEB_DIR", "web"));

        WebServer app = new WebServer(webDir, surge);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", app::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("Ride Booking System running on http://localhost:" + port
                + "  (surge x" + surge + ", web dir: " + app.webDir + ")");
    }

    private static final Object[][] CITIES = {
        {"Delhi", 28.6139, 77.2090}, {"Mumbai", 19.0760, 72.8777}, {"Bengaluru", 12.9716, 77.5946},
        {"Hyderabad", 17.3850, 78.4867}, {"Chennai", 13.0827, 80.2707}, {"Kolkata", 22.5726, 88.3639},
        {"Pune", 18.5204, 73.8567}, {"Ahmedabad", 23.0225, 72.5714}, {"Jaipur", 26.9124, 75.7873},
        {"Lucknow", 26.8467, 80.9462}, {"Kochi", 9.9312, 76.2673}, {"Chandigarh", 30.7333, 76.7794},
    };
    // Same pattern in every city (degrees from the city centre, about 0.5 - 2.5 km).
    private static final double[][] OFFSETS = {
        {0.006, 0.004}, {-0.012, 0.010}, {0.018, -0.008}, {0.004, -0.016},
        {-0.008, -0.006}, {-0.002, 0.021}, {0.014, 0.015}, {-0.019, -0.012},
    };
    private static final VehicleType[] SEED_TYPES = {
        VehicleType.SEDAN, VehicleType.SEDAN, VehicleType.SEDAN, VehicleType.AUTO,
        VehicleType.AUTO, VehicleType.BIKE, VehicleType.BIKE, VehicleType.SUV,
    };
    private static final String[] SEED_NAMES = {"Ravi", "Sunil", "Farhan", "Imran", "Kiran", "Deepak", "Meena", "Arjun"};

    private void seedDrivers() {
        for (Object[] city : CITIES) {
            double lat = (Double) city[1], lng = (Double) city[2];
            for (int i = 0; i < OFFSETS.length; i++) {
                Driver d = new Driver(SEED_NAMES[i], SEED_TYPES[i],
                        new Location(lat + OFFSETS[i][0], lng + OFFSETS[i][1]));
                rideService.registerDriver(d);
                d.goOnline();
            }
        }
    }

    // ------------------------------------------------------------------ routing

    private static class HttpError extends RuntimeException {
        final int status;
        HttpError(int status, String message) { super(message); this.status = status; }
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                api(ex, path);
            } else {
                serveStatic(ex, path);
            }
        } catch (HttpError e) {
            send(ex, e.status, Map.of("error", e.getMessage()));
        } catch (NoDriverAvailableException | InvalidStateTransitionException e) {
            send(ex, 409, Map.of("error", e.getMessage()));
        } catch (TripNotFoundException e) {
            send(ex, 404, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            send(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, Map.of("error", "Internal error"));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        Map<String, String> p = params(ex);
        String[] seg = path.substring("/api/".length()).split("/");

        switch (seg[0]) {
            case "health" -> send(ex, 200, Map.of("status", "ok"));

            case "config" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("radiusKm", RADIUS_KM);
                m.put("surge", surge);
                m.put("vehicleTypes", List.of("BIKE", "AUTO", "SEDAN", "SUV"));
                m.put("roadFactor", RideService.ROAD_FACTOR);
                List<Object> cities = new ArrayList<>();
                for (Object[] c : CITIES) {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("name", c[0]);
                    cm.put("lat", c[1]);
                    cm.put("lng", c[2]);
                    cities.add(cm);
                }
                m.put("cities", cities);
                send(ex, 200, m);
            }

            case "drivers" -> {
                if (method.equals("GET")) {
                    List<Object> out = new ArrayList<>();
                    for (Driver d : driverRepository.findAll()) out.add(driverJson(d, null));
                    send(ex, 200, out);
                } else if (method.equals("POST")) {
                    String name = p.getOrDefault("name", "Driver " + driverCounter.incrementAndGet());
                    Driver d = new Driver(clip(name), vehicleType(p), location(p, "lat", "lng"));
                    rideService.registerDriver(d);
                    d.goOnline();
                    send(ex, 201, driverJson(d, null));
                } else {
                    throw new HttpError(405, "Method not allowed");
                }
            }

            case "nearby" -> {
                requireGet(method);
                Location pickup = location(p, "lat", "lng");
                List<Object> out = new ArrayList<>();
                for (Driver d : rideService.findNearbyDrivers(pickup, vehicleType(p))) {
                    out.add(driverJson(d, pickup));
                }
                send(ex, 200, out);
            }

            case "estimate" -> {
                requireGet(method);
                Location pickup = location(p, "pickupLat", "pickupLng");
                Location drop = location(p, "dropLat", "dropLng");
                VehicleType type = vehicleType(p);
                List<Driver> nearby = rideService.findNearbyDrivers(pickup, type);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("distanceKm", round(locationService.distanceKm(pickup, drop)));
                m.put("roadDistanceKm", round(rideService.roadDistanceKm(pickup, drop)));
                m.put("fare", rideService.estimateFare(pickup, drop, type));
                m.put("nearbyDrivers", nearby.size());
                if (nearby.isEmpty()) {
                    m.put("nearestDriverKm", null);
                    m.put("etaMinutes", null);
                } else {
                    double nearestKm = locationService.distanceKm(nearby.get(0).getCurrentLocation(), pickup);
                    m.put("nearestDriverKm", round(nearestKm));
                    m.put("etaMinutes", rideService.estimateEtaMinutes(nearestKm));
                }
                send(ex, 200, m);
            }

            case "rides" -> {
                if (!method.equals("POST")) throw new HttpError(405, "Use POST");
                Rider rider = new Rider(clip(p.getOrDefault("riderName", "Rider")));
                Trip trip = rideService.requestRide(rider,
                        location(p, "pickupLat", "pickupLng"),
                        location(p, "dropLat", "dropLng"),
                        vehicleType(p));
                send(ex, 201, tripJson(trip));
            }

            case "trips" -> {
                if (seg.length == 1) {
                    requireGet(method);
                    List<Object> out = new ArrayList<>();
                    for (Trip t : tripRepository.findAll()) out.add(tripJson(t));
                    send(ex, 200, out);
                } else if (seg.length == 2) {
                    requireGet(method);
                    send(ex, 200, tripJson(rideService.getTrip(seg[1])));
                } else if (seg.length == 3) {
                    if (!method.equals("POST")) throw new HttpError(405, "Use POST");
                    String id = seg[1];
                    Trip t = switch (seg[2]) {
                        case "arrived"  -> rideService.driverArrived(id);
                        case "start"    -> rideService.startTrip(id);
                        case "complete" -> rideService.completeTrip(id);
                        case "cancel"   -> rideService.cancelTrip(id);
                        default -> throw new HttpError(404, "Unknown action: " + seg[2]);
                    };
                    send(ex, 200, tripJson(t));
                } else {
                    throw new HttpError(404, "Not found");
                }
            }

            default -> throw new HttpError(404, "Not found");
        }
    }

    private static void requireGet(String method) {
        if (!method.equals("GET")) throw new HttpError(405, "Use GET");
    }

    // ------------------------------------------------------------------ JSON shapes

    private Map<String, Object> driverJson(Driver d, Location from) {
        Location loc = d.getCurrentLocation();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("vehicleType", d.getVehicleType().name());
        m.put("status", d.getStatus().name());
        m.put("rating", d.getRating());
        m.put("lat", loc.latitude());
        m.put("lng", loc.longitude());
        if (from != null) m.put("distanceKm", round(locationService.distanceKm(loc, from)));
        return m;
    }

    private Map<String, Object> tripJson(Trip t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("status", t.getStatus().name());
        m.put("riderName", t.getRider().getName());
        m.put("vehicleType", t.getVehicleType().name());
        m.put("pickup", locJson(t.getPickup()));
        m.put("drop", locJson(t.getDrop()));
        m.put("fare", t.getFare());
        Driver d = t.getDriver();
        m.put("driver", d == null ? null : driverJson(d, null));
        return m;
    }

    private static Map<String, Object> locJson(Location l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", l.latitude());
        m.put("lng", l.longitude());
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String clip(String s) {
        String t = s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Name can not be empty");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    // ------------------------------------------------------------------ request parsing

    /** Query string + form-encoded body, merged. */
    private Map<String, String> params(HttpExchange ex) throws IOException {
        Map<String, String> m = parseForm(ex.getRequestURI().getRawQuery());
        if (ex.getRequestMethod().equals("POST")) {
            try (InputStream in = ex.getRequestBody()) {
                String body = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
                m.putAll(parseForm(body));
            }
        }
        return m;
    }

    private static Map<String, String> parseForm(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private static double number(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null) throw new IllegalArgumentException("Missing parameter: " + key);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + key + ": " + v);
        }
    }

    private static Location location(Map<String, String> p, String latKey, String lngKey) {
        return new Location(number(p, latKey), number(p, lngKey));
    }

    private static VehicleType vehicleType(Map<String, String> p) {
        String v = p.get("type");
        if (v == null) throw new IllegalArgumentException("Missing parameter: type");
        try {
            return VehicleType.valueOf(v.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vehicle type: " + v);
        }
    }

    // ------------------------------------------------------------------ responses

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) throw new HttpError(405, "Use GET");
        String rel = path.equals("/") ? "index.html" : path.substring(1);
        Path file = webDir.resolve(rel).normalize();
        if (!file.startsWith(webDir) || !Files.isRegularFile(file)) {
            throw new HttpError(404, "Not found");
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js"))   return "text/javascript; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        if (name.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }
}
