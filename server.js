// Ride Booking System - real backend (Node.js, no dependencies)
// Run:  node server.js     then open  http://localhost:8080
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT) || 8080;
const HTML_FILE = path.join(__dirname, 'index.html');
const DATA_FILE = path.join(__dirname, 'data.json');

const RADIUS_KM = 5, ROAD = 1.3, AVG_SPEED = 30, ETA_SPEED = 25, SURGE = 1.0;
const RATES = { BIKE: [20, 6, 1], AUTO: [30, 9, 1.5], SEDAN: [50, 12, 2], SUV: [80, 18, 3] };
const CITIES = [
  { name: 'Delhi', lat: 28.6139, lng: 77.2090 }, { name: 'Mumbai', lat: 19.0760, lng: 72.8777 },
  { name: 'Bengaluru', lat: 12.9716, lng: 77.5946 }, { name: 'Hyderabad', lat: 17.3850, lng: 78.4867 },
  { name: 'Chennai', lat: 13.0827, lng: 80.2707 }, { name: 'Kolkata', lat: 22.5726, lng: 88.3639 },
  { name: 'Pune', lat: 18.5204, lng: 73.8567 }, { name: 'Ahmedabad', lat: 23.0225, lng: 72.5714 },
  { name: 'Jaipur', lat: 26.9124, lng: 75.7873 }, { name: 'Lucknow', lat: 26.8467, lng: 80.9462 },
  { name: 'Kochi', lat: 9.9312, lng: 76.2673 }, { name: 'Chandigarh', lat: 30.7333, lng: 76.7794 }, { name: 'Bhopal', lat: 23.2599, lng: 77.4126 }, { name: 'Indore', lat: 22.7196, lng: 75.8577 }, { name: 'Nagpur', lat: 21.1458, lng: 79.0882 }, { name: 'Surat', lat: 21.1702, lng: 72.8311 }, { name: 'Patna', lat: 25.5941, lng: 85.1376 }, { name: 'Varanasi', lat: 25.3176, lng: 82.9739 }, { name: 'Bhubaneswar', lat: 20.2961, lng: 85.8245 }, { name: 'Visakhapatnam', lat: 17.6868, lng: 83.2185 }, { name: 'Coimbatore', lat: 11.0168, lng: 76.9558 }, { name: 'Guwahati', lat: 26.1445, lng: 91.7362 }
];
const OFFSETS = [[0.006, 0.004], [-0.012, 0.010], [0.018, -0.008], [0.004, -0.016],
                 [-0.008, -0.006], [-0.002, 0.021], [0.014, 0.015], [-0.019, -0.012]];
const SEED_TYPES = ['SEDAN', 'SEDAN', 'SEDAN', 'AUTO', 'AUTO', 'BIKE', 'BIKE', 'SUV'];
const SEED_NAMES = ['Ravi', 'Sunil', 'Farhan', 'Imran', 'Kiran', 'Deepak', 'Meena', 'Arjun'];
const ACTION_TEXT = { arrived: 'mark driver arrived', start: 'start the trip', complete: 'complete the trip', cancel: 'cancel the trip' };
const FLOW = {
  REQUESTED:       { cancel: 'CANCELLED' },
  DRIVER_ASSIGNED: { arrived: 'DRIVER_ARRIVED', cancel: 'CANCELLED' },
  DRIVER_ARRIVED:  { start: 'IN_PROGRESS', cancel: 'CANCELLED' },
  IN_PROGRESS:     { complete: 'COMPLETED' },
  COMPLETED: {}, CANCELLED: {}
};

class HttpError extends Error { constructor(code, msg) { super(msg); this.code = code; } }

// ---------------- state + persistence ----------------
let drivers = [];
let trips = new Map();

const uid = () => crypto.randomUUID();
function addDriver(name, type, lat, lng) {
  const d = { id: uid(), name, vehicleType: type, status: 'AVAILABLE', rating: 5.0, lat, lng };
  drivers.push(d);
  return d;
}
function save() {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify({ drivers, trips: Array.from(trips.values()).map(t => ({ ...t, driver: t.driver ? t.driver.id : null })) }));
  } catch (e) { console.error('Could not save data:', e.message); }
}
function load() {
  try {
    const j = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    drivers = j.drivers || [];
    const byId = new Map(drivers.map(d => [d.id, d]));
    (j.trips || []).forEach(t => { t.driver = t.driver ? byId.get(t.driver) || null : null; trips.set(t.id, t); });
  } catch (e) { /* first run */ }
  if (!drivers.length) {
    CITIES.forEach(c => OFFSETS.forEach((o, i) => addDriver(SEED_NAMES[i], SEED_TYPES[i], c.lat + o[0], c.lng + o[1])));
    save();
  }
}

// ---------------- logic ----------------
function hav(aLat, aLng, bLat, bLng) {
  const R = 6371.0088, rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad, dLng = (bLng - aLng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
const round2 = (v) => Math.round(v * 100) / 100;
function num(p, key) {
  if (p[key] === undefined || p[key] === null || p[key] === '') throw new HttpError(400, 'Missing parameter: ' + key);
  const v = Number(p[key]);
  if (!isFinite(v)) throw new HttpError(400, 'Invalid number for ' + key + ': ' + p[key]);
  return v;
}
function vtype(p) {
  if (!p.type) throw new HttpError(400, 'Missing parameter: type');
  const t = String(p.type).toUpperCase();
  if (!RATES[t]) throw new HttpError(400, 'Invalid vehicle type: ' + p.type);
  return t;
}
function checkLoc(lat, lng) {
  if (lat < -90 || lat > 90) throw new HttpError(400, 'Invalid latitude: ' + lat);
  if (lng < -180 || lng > 180) throw new HttpError(400, 'Invalid longitude: ' + lng);
}
const roadKm = (a, b, c, d) => hav(a, b, c, d) * ROAD;
function fareFor(type, km, min) {
  const r = RATES[type];
  return round2((r[0] + r[1] * km + r[2] * min) * SURGE);
}
function estimateFare(pl, pg, dl, dg, type) {
  const km = roadKm(pl, pg, dl, dg);
  return fareFor(type, km, km / AVG_SPEED * 60);
}
// Uses the real road distance/time sent by the page (OSRM) when it is plausible, else straight-line x 1.3
function routeInfo(p, pl, pg, dl, dg) {
  const straight = hav(pl, pg, dl, dg);
  let km = straight * ROAD, min = km / AVG_SPEED * 60, real = false;
  const rk = Number(p.roadKm), rm = Number(p.durationMin);
  if (p.roadKm && isFinite(rk) && rk >= straight * 0.99 && rk <= straight * 5 + 2) {
    km = rk; real = true;
    min = (p.durationMin && isFinite(rm) && rm > 0 && rm < 6000) ? rm : km / AVG_SPEED * 60;
  }
  return { km, min, real };
}
const etaMinutes = (straightKm) => Math.max(1, Math.round(straightKm * ROAD / ETA_SPEED * 60));
function nearby(lat, lng, type) {
  return drivers
    .filter(d => d.vehicleType === type && d.status === 'AVAILABLE')
    .map(d => ({ d, dist: hav(d.lat, d.lng, lat, lng) }))
    .filter(x => x.dist <= RADIUS_KM)
    .sort((a, b) => a.dist - b.dist);
}
function tripJson(t) {
  return { id: t.id, status: t.status, riderName: t.riderName, vehicleType: t.vehicleType,
    pickup: { ...t.pickup }, drop: { ...t.drop }, fare: t.fare, roadKm: t.roadKm, driver: t.driver ? { ...t.driver } : null };
}
function getTrip(id) {
  const t = trips.get(id);
  if (!t) throw new HttpError(404, 'Trip not found: ' + id);
  return t;
}
function act(t, action) {
  const next = FLOW[t.status][action];
  if (!next) throw new HttpError(409, 'Cannot ' + ACTION_TEXT[action] + ' when trip is ' + t.status);
  t.status = next;
}

function route(method, pathname, p) {
  const seg = pathname.replace(/^\/+/, '').replace(/^api\//, '').split('/');
  switch (seg[0]) {
    case 'health': return { status: 'ok' };
    case 'config': return { radiusKm: RADIUS_KM, surge: SURGE, vehicleTypes: Object.keys(RATES), roadFactor: ROAD, cities: CITIES };
    case 'drivers': {
      if (method === 'GET') return drivers;
      const lat = num(p, 'lat'), lng = num(p, 'lng'); checkLoc(lat, lng);
      const d = addDriver(String(p.name || 'Driver ' + (drivers.length + 1)).slice(0, 40), vtype(p), lat, lng);
      save();
      return d;
    }
    case 'estimate': {
      const pl = num(p, 'pickupLat'), pg = num(p, 'pickupLng'), dl = num(p, 'dropLat'), dg = num(p, 'dropLng');
      checkLoc(pl, pg); checkLoc(dl, dg);
      const type = vtype(p), near = nearby(pl, pg, type), ri = routeInfo(p, pl, pg, dl, dg);
      return {
        distanceKm: round2(hav(pl, pg, dl, dg)), roadDistanceKm: round2(ri.km), realRoute: ri.real,
        fare: fareFor(type, ri.km, ri.min), nearbyDrivers: near.length,
        nearestDriverKm: near.length ? round2(near[0].dist) : null,
        etaMinutes: near.length ? etaMinutes(near[0].dist) : null
      };
    }
    case 'rides': {
      if (method !== 'POST') throw new HttpError(405, 'Use POST');
      const pl = num(p, 'pickupLat'), pg = num(p, 'pickupLng'), dl = num(p, 'dropLat'), dg = num(p, 'dropLng');
      checkLoc(pl, pg); checkLoc(dl, dg);
      const type = vtype(p);
      const best = nearby(pl, pg, type)[0];
      if (!best) throw new HttpError(409, 'No ' + type + ' driver available within ' + RADIUS_KM + ' km');
      const ri = routeInfo(p, pl, pg, dl, dg);
      const t = { id: uid(), status: 'REQUESTED', riderName: String(p.riderName || 'Rider').trim().slice(0, 40) || 'Rider',
                  vehicleType: type, pickup: { lat: pl, lng: pg }, drop: { lat: dl, lng: dg },
                  fare: fareFor(type, ri.km, ri.min), roadKm: ri.km, min: ri.min, driver: null };
      best.d.status = 'ON_TRIP';
      t.driver = best.d;
      t.status = 'DRIVER_ASSIGNED';
      trips.set(t.id, t);
      save();
      return tripJson(t);
    }
    case 'trips': {
      if (seg.length === 1) return Array.from(trips.values()).map(tripJson);
      const t = getTrip(seg[1]);
      if (seg.length === 2) return tripJson(t);
      const action = seg[2];
      if (!ACTION_TEXT[action]) throw new HttpError(404, 'Unknown action: ' + action);
      if (method !== 'POST') throw new HttpError(405, 'Use POST');
      act(t, action);
      if (action === 'complete') {
        t.fare = t.roadKm ? fareFor(t.vehicleType, t.roadKm, t.min) : estimateFare(t.pickup.lat, t.pickup.lng, t.drop.lat, t.drop.lng, t.vehicleType);
        if (t.driver) { t.driver.lat = t.drop.lat; t.driver.lng = t.drop.lng; t.driver.status = 'AVAILABLE'; }
      }
      if (action === 'cancel' && t.driver) t.driver.status = 'AVAILABLE';
      save();
      return tripJson(t);
    }
    default: throw new HttpError(404, 'Not found');
  }
}

// ---------------- http ----------------
function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', c => { raw += c; if (raw.length > 1e6) { reject(new HttpError(413, 'Body too large')); req.destroy(); } });
    req.on('end', () => {
      if (!raw) return resolve({});
      const ct = req.headers['content-type'] || '';
      try {
        if (ct.includes('application/json')) resolve(JSON.parse(raw));
        else resolve(Object.fromEntries(new URLSearchParams(raw).entries()));
      } catch (e) { reject(new HttpError(400, 'Invalid request body')); }
    });
    req.on('error', reject);
  });
}
function send(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
  res.end(body);
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type' });
    return res.end();
  }
  if (u.pathname.startsWith('/api/')) {
    try {
      const params = { ...Object.fromEntries(u.searchParams.entries()), ...(req.method === 'POST' ? await readBody(req) : {}) };
      send(res, 200, route(req.method, u.pathname, params));
    } catch (e) {
      if (e instanceof HttpError) send(res, e.code, { error: e.message });
      else { console.error(e); send(res, 500, { error: 'Internal server error' }); }
    }
    return;
  }
  if (u.pathname === '/' || u.pathname === '/index.html') {
    fs.readFile(HTML_FILE, (err, buf) => {
      if (err) { res.writeHead(500, { 'Content-Type': 'text/plain' }); return res.end('index.html not found next to server.js'); }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(buf);
    });
    return;
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not found');
});

load();
server.on('error', (e) => {
  if (e.code === 'EADDRINUSE') console.error('Port ' + PORT + ' already in use. Try:  PORT=3000 node server.js');
  else console.error(e);
  process.exit(1);
});
server.listen(PORT, () => console.log('Ride Booking server running: http://localhost:' + PORT));
