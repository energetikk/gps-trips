'use strict';

/**
 * Демонстрационные поездки для страницы истории.
 *
 *   TRACK_TOKEN=... node seed.js --base https://gps.arzamas.space
 *   TRACK_TOKEN=... node seed.js --base https://gps.arzamas.space --clear
 *
 * Без --clear загружает несколько тестовых маршрутов по Нижнему Новгороду
 * за последние дни. Все они помечены source = "demo-...", поэтому --clear
 * удаляет только их и не трогает реальные поездки.
 */

const BASE = value('--base', process.env.TRIPS_BASE || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const TOKEN = value('--token', process.env.TRACK_TOKEN || '').trim();
const DEVICE = value('--device', 'car');
const CLEAR = process.argv.includes('--clear');

if (!/^https?:\/\/.+/.test(BASE)) {
  console.error('Укажите адрес сервера: --base https://gps.arzamas.space');
  process.exit(1);
}
if (!TOKEN) {
  console.error('Нужен токен: TRACK_TOKEN=... node seed.js --base ...');
  process.exit(1);
}

const ROUTES = [
  // набережная Федоровского -> Нижневолжская набережная
  [
    [56.3298, 44.0021], [56.3309, 44.0078], [56.3316, 44.0142],
    [56.3292, 44.0215], [56.3254, 44.0268], [56.3210, 44.0310],
  ],
  // Большая Покровская
  [
    [56.3098, 43.9955], [56.3124, 43.9968], [56.3150, 43.9988],
    [56.3178, 44.0010], [56.3205, 44.0036], [56.3230, 44.0062],
  ],
  // проспект Гагарина
  [
    [56.2890, 43.9410], [56.2930, 43.9450], [56.2970, 43.9490],
    [56.3010, 43.9535], [56.3050, 43.9580], [56.3095, 43.9625],
  ],
  // Сормовское шоссе
  [
    [56.3510, 43.8720], [56.3470, 43.8790], [56.3430, 43.8865],
    [56.3390, 43.8940], [56.3350, 43.9010], [56.3310, 43.9080],
  ],
];

const now = new Date();

function at(daysAgo, hour, minute) {
  const date = new Date(now);
  date.setDate(date.getDate() - daysAgo);
  date.setHours(hour, minute, 0, 0);
  return date.getTime();
}

function pad(n) {
  return String(n).padStart(2, '0');
}

function dateKey(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function distanceM(a, b) {
  const R = 6371000;
  const dLat = ((b[0] - a[0]) * Math.PI) / 180;
  const dLon = ((b[1] - a[1]) * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos((a[0] * Math.PI) / 180) * Math.cos((b[0] * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Проводит маршрут по точкам с шагом ~25 секунд и лёгким «дрожанием» GPS. */
function buildTrip(startMs, route, index) {
  const track = [];
  const speeds = [];
  let time = startMs;
  let seed = 17 + index * 31;

  function rnd() {
    seed = (seed * 1103515245 + 12345) % 2147483648;
    return seed / 2147483648;
  }

  for (let i = 0; i < route.length - 1; i++) {
    const from = route[i];
    const to = route[i + 1];
    const steps = 8;
    for (let s = 0; s < steps; s++) {
      const f = s / steps;
      const lat = from[0] + (to[0] - from[0]) * f + (rnd() - 0.5) * 0.00012;
      const lon = from[1] + (to[1] - from[1]) * f + (rnd() - 0.5) * 0.00018;
      const speed = Math.max(8, 38 + Math.sin((i * steps + s) / 3.5) * 18 + (rnd() - 0.5) * 8);
      if (track.length > 0) time += 25_000 + Math.round((rnd() - 0.5) * 4000);
      track.push([time, Number(lat.toFixed(6)), Number(lon.toFixed(6)), Number(speed.toFixed(1))]);
      speeds.push(speed);
    }
  }

  let distance = 0;
  for (let i = 1; i < track.length; i++) {
    const a = [track[i - 1][1], track[i - 1][2]];
    const b = [track[i][1], track[i][2]];
    distance += distanceM(a, b);
  }
  const movingMs = time - startMs;
  const movingSec = Math.round((movingMs / 1000) * 0.9);
  const avg = movingSec > 0 ? (distance / movingSec) * 3.6 : 0;
  const max = Math.max(...speeds);

  return {
    id: `demo-${dateKey(startMs)}-${index}@${startMs}`,
    source: `demo-${dateKey(startMs)}.nmea`,
    device: DEVICE,
    startMs,
    endMs: time,
    distanceM: Math.round(distance * 10) / 10,
    movingSec,
    maxSpeedKmh: Math.round(max * 10) / 10,
    avgSpeedKmh: Math.round(avg * 10) / 10,
    timeReliable: true,
    track,
  };
}

function schedule() {
  return [
    { daysAgo: 3, hour: 8, minute: 20, route: 0 },
    { daysAgo: 3, hour: 18, minute: 10, route: 1 },
    { daysAgo: 2, hour: 8, minute: 40, route: 2 },
    { daysAgo: 2, hour: 18, minute: 25, route: 3 },
    { daysAgo: 1, hour: 8, minute: 15, route: 1 },
    { daysAgo: 1, hour: 17, minute: 50, route: 2 },
    { daysAgo: 0, hour: 8, minute: 35, route: 0 },
  ];
}

async function request(pathname, options) {
  const response = await fetch(`${BASE}${pathname}`, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 200)}`);
  }
  return response.status === 204 ? null : response.json();
}

async function clear() {
  const result = await request('/api/trips/demo', {
    method: 'DELETE',
    headers: { 'X-Token': TOKEN },
  });
  console.log(`Удалено демо-поездок: ${result.removed}`);
}

async function seed() {
  const trips = schedule().map((item, index) =>
    buildTrip(at(item.daysAgo, item.hour, item.minute), ROUTES[item.route], index + 1)
  );

  let ok = 0;
  for (const trip of trips) {
    try {
      await request('/api/trips', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Token': TOKEN },
        body: JSON.stringify(trip),
      });
      ok++;
      const km = (trip.distanceM / 1000).toFixed(1).replace('.', ',');
      console.log(`ok  ${trip.id}  ${km} км, ${trip.track.length} точек`);
    } catch (error) {
      console.error(`err ${trip.id}: ${error.message}`);
    }
  }
  console.log(`Загружено ${ok} из ${trips.length} демо-поездок на ${BASE}`);
  if (ok > 0) console.log('Убрать тестовые данные: добавьте --clear');
}

function value(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

if (CLEAR) {
  clear().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
} else {
  seed().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
