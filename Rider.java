package com.ridebooking.model;

import java.util.Objects;
import java.util.UUID;

public class Rider {
    private final String id;
    private final String name;
    private double rating;

    public Rider(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name");
        this.rating = 5.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    @Override
    public String toString() {
        return "Rider{" + name + "}";
    }
}
