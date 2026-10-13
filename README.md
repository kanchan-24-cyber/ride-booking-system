# Ride Booking System

A ride booking backend in plain Java, with a live website on top.
It matches riders with nearby drivers, quotes a fare for every vehicle type, and walks a trip through a
validated status flow. No frameworks and no database: just JDK 17+.

## Features

- **Nearby driver search** using the Haversine formula, limited to a 5 km radius.
- **Driver-rider matching** behind a `MatchingStrategy` interface (nearest driver, or highest rated).
- **Fare calculation** behind a `FareStrategy` interface: base fare + per km + per minute, per vehicle type
  (Bike, Auto, Sedan, SUV), with an optional surge multiplier (decorator).
- **Trip status flow** as a State pattern: `DRIVER_ASSIGNED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED`
  (plus `CANCELLED`). Illegal moves, like starting a trip before the driver arrived, are rejected.
- **No double booking.** `Driver.tryReserve()` is atomic, so 100 riders asking for one driver at the same
  time produce exactly one winner. This is covered by tests.
- **Live website**: India map, ride options with fare and ETA per vehicle, fare breakdown, booking,
  and a trip simulation where the car drives from pickup to drop.
- **REST API** built on the JDK's own `HttpServer` (no Maven needed).

## Quick start

You need JDK 17 or newer (`java -version`).

| Where | Website | Tests |
|---|---|---|
| Windows | double-click `start-web.bat` | double-click `run-tests.bat` |
| Mac / Linux / Codespaces | `sh start-web.sh` | `sh run-tests.sh` |

Then open http://localhost:8080. In GitHub Codespaces, click "Open in Browser" on the port 8080 popup.

Manual commands, if you prefer:

```bash
javac -d out $(find src -name "*.java")
java -cp out com.ridebooking.web.WebServer        # website + API
java -cp out com.ridebooking.AllTests             # 21 tests, including concurrency
java -cp out com.ridebooking.RideBookingApp       # console demo
```

Optional environment variables: `PORT` (default 8080), `SURGE` (fare multiplier such as `1.5`),
`WEB_DIR` (default `web`).

## Using the website

1. Pick a city (12 are built in) or stay on the India view.
2. Tap the map to set pickup, then drop. You see Bike, Auto, Sedan and SUV with fare and ETA for each.
3. Choose a vehicle and press Book.
4. Press "Simulate the whole trip", or step through manually. Press a button in the wrong order to see the
   state machine reject it.

**Demo mode:** `web/index.html` also works without the Java server. If it cannot reach the server (for example
when opened as a plain file) it runs a JavaScript copy of the same rules and shows a "Demo mode" badge. With
the server running, the badge says "Java server connected".

## REST API

POST bodies are form-encoded. Responses are JSON.

| Method | Path | Parameters |
|---|---|---|
| GET | `/api/health` | |
| GET | `/api/config` | radius, surge, road factor, city list |
| GET | `/api/drivers` | |
| POST | `/api/drivers` | `type`, `lat`, `lng`, `name` (optional) |
| GET | `/api/nearby` | `lat`, `lng`, `type` |
| GET | `/api/estimate` | `pickupLat`, `pickupLng`, `dropLat`, `dropLng`, `type` |
| POST | `/api/rides` | `riderName`, `pickupLat`, `pickupLng`, `dropLat`, `dropLng`, `type` |
| GET | `/api/trips`, `/api/trips/{id}` | |
| POST | `/api/trips/{id}/arrived`, `/start`, `/complete`, `/cancel` | |

Errors: `400` bad input, `404` unknown trip, `409` no driver available or illegal status change.

```bash
curl -X POST localhost:8080/api/rides \
  -d "riderName=Aditya&pickupLat=12.9756&pickupLng=77.6066&dropLat=12.9352&dropLng=77.6245&type=SEDAN"
```

## How it works

| Concern | Approach |
|---|---|
| Matching | `MatchingStrategy` returns a ranked list, so if the best driver is taken at the same instant the next one is tried |
| Fare | `fare = base + perKm * roadKm + perMinute * minutes`, where `roadKm = straight line * 1.3` |
| Trip flow | `TripState` makes every action illegal by default; each state class allows only its valid moves |
| Concurrency | `Driver.tryReserve()` is a synchronized check-and-set from AVAILABLE to ON_TRIP |
| Storage | in-memory repositories (`ConcurrentHashMap`) behind small classes, easy to swap for a database |

## Project structure

```
src/main/java/com/ridebooking
  model/        Location, Rider, Driver, Trip, VehicleType, DriverStatus, TripStatus
  state/        TripState and six state classes
  strategy/     MatchingStrategy (Nearest, HighestRating), FareStrategy (Base, Surge)
  service/      LocationService (Haversine), RideService (orchestration)
  repository/   DriverRepository, TripRepository
  exception/    NoDriverAvailable, InvalidStateTransition, TripNotFound
  web/          WebServer (API + static files), Json
  RideBookingApp.java   console demo
src/test/java/com/ridebooking/AllTests.java   plain-Java test runner (no libraries)
web/index.html          the website
Dockerfile              for deployment
```

## Deploy

A `Dockerfile` is included. On Render, Railway, Fly.io or Koyeb, create a Web Service from this repository and
choose the Docker runtime. The platform sets `PORT`. Data lives in memory, so it resets on every restart.

## Limitations

- Drivers are demo data seeded in 12 Indian cities. There is no real driver app or live location feed.
- Distance is a straight line times 1.3, not routing over real roads. Fares are an approximation, not the
  pricing of any real ride-hailing service.
- Everything is in memory: no database, no login, no payments.
- The India outline is simplified for a backdrop, so it is not suitable as an official boundary map.

## Roadmap

- Spring Boot, PostgreSQL and JPA instead of in-memory storage
- Redis GEO for nearby search at scale
- WebSocket live driver locations
- Driver accept/timeout flow, surge from demand and supply, payments with idempotency keys

## Data and attributions

- **India outline:** simplified from [india-maps-data](https://github.com/udit-001/india-maps-data) by
  Udit, built on the [DataMeet](http://datameet.org/) India community's boundary data,
  licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Changes made: simplified geometry.
- **Map library:** [Leaflet](https://leafletjs.com/) (BSD-2-Clause), loaded from a CDN.
- **Street map tiles:** (c) [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors and
  [CARTO](https://carto.com/attributions). Check CARTO's terms before any commercial use.

## License

The code is released under the [MIT License](LICENSE). The data and services listed above keep their own
licenses.
