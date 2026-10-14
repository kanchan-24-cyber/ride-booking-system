package com.ridebooking.repository;

import com.ridebooking.model.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TripRepository {
    private final Map<String, Trip> trips = new ConcurrentHashMap<>();

    public void save(Trip trip) {
        trips.put(trip.getId(), trip);
    }

    public Optional<Trip> findById(String id) {
        return Optional.ofNullable(trips.get(id));
    }

    public List<Trip> findAll() {
        return new ArrayList<>(trips.values());
    }
}
