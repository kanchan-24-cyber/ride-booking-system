package com.ridebooking.service;

import com.ridebooking.model.Location;

public class LocationService {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Haversine formula: straight-line distance over the earth's surface, in km. */
    public double distanceKm(Location a, Location b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
