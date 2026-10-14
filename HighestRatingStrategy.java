package com.ridebooking.strategy;

import com.ridebooking.model.Driver;
import com.ridebooking.model.Location;
import com.ridebooking.service.LocationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Best rating first; if ratings tie, the nearer driver wins. */
public class HighestRatingStrategy implements MatchingStrategy {
    private final LocationService locationService;

    public HighestRatingStrategy(LocationService locationService) {
        this.locationService = locationService;
    }

    private record Scored(Driver driver, double rating, double distanceKm) {}

    @Override
    public List<Driver> rank(List<Driver> candidates, Location pickup) {
        List<Scored> scored = new ArrayList<>();
        for (Driver d : candidates) {
            scored.add(new Scored(d, d.getRating(),
                    locationService.distanceKm(d.getCurrentLocation(), pickup)));
        }
        scored.sort(Comparator.comparingDouble(Scored::rating).reversed()
                .thenComparingDouble(Scored::distanceKm));

        List<Driver> ranked = new ArrayList<>();
        for (Scored s : scored) {
            ranked.add(s.driver());
        }
        return ranked;
    }
}
