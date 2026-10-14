package com.ridebooking.repository;

import com.ridebooking.model.Driver;
import com.ridebooking.model.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store. Phase 2 replaces this with PostgreSQL / Redis. */
public class DriverRepository {
    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

    public void save(Driver driver) {
        drivers.put(driver.getId(), driver);
    }

    public Optional<Driver> findById(String id) {
        return Optional.ofNullable(drivers.get(id));
    }

    public List<Driver> findAll() {
        return new ArrayList<>(drivers.values());
    }

    public List<Driver> findAvailableByType(VehicleType type) {
        List<Driver> result = new ArrayList<>();
        for (Driver d : drivers.values()) {
            if (d.getVehicleType() == type && d.isAvailable()) {
                result.add(d);
            }
        }
        return result;
    }
}
