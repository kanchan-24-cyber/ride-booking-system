package com.ridebooking.strategy;

import com.ridebooking.model.Driver;
import com.ridebooking.model.Location;
import com.ridebooking.service.LocationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NearestDriverStrategy implements MatchingStrategy {
    private final LocationService locationService;

    public NearestDriverStrategy(LocationService locationService) {
        this.locationService = locationService;
    }

    private record Scored(Driver driver, double distanceKm) {}

    @Override
    public List<Driver> rank(List<Driver> candidates, Location pickup) {
        // Take a snapshot of each distance first: driver locations can change
        // while we sort, and a changing comparator breaks sorting.
        List<Scored> scored = new ArrayList<>();
        for (Driver d : candidates) {
            scored.add(new Scored(d, locationService.distanceKm(d.getCurrentLocation(), pickup)));
        }
        scored.sort(Comparator.comparingDouble(Scored::distanceKm));

        List<Driver> ranked = new ArrayList<>();
        for (Scored s : scored) {
            ranked.add(s.driver());
        }
        return ranked;
    }
}
