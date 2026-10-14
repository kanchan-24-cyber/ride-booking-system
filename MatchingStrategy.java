package com.ridebooking.strategy;

import com.ridebooking.model.Driver;
import com.ridebooking.model.Location;

import java.util.List;

public interface MatchingStrategy {
    /**
     * Returns the candidates ordered best-first. We return a whole ranked list
     * (not just one driver) so that if the best driver gets taken by another
     * rider at the same instant, the service can fall back to the next one.
     */
    List<Driver> rank(List<Driver> candidates, Location pickup);
}
