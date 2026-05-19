import {triangulateRing} from './triangulate.js';

type Vec3 = [number, number, number];
type RGB = [number, number, number];

const NAMED_COLORS: Record<string, RGB> = {
  red: [1, 0, 0], green: [0, 0.5, 0], blue: [0, 0, 1],
  white: [1, 1, 1], black: [0, 0, 0], yellow: [1, 1, 0],
  orange: [1, 0.647, 0], purple: [0.502, 0, 0.502], cyan: [0, 1, 1],
  magenta: [1, 0, 1], grey: [0.502, 0.502, 0.502], gray: [0.502, 0.502, 0.502],
  brown: [0.647, 0.165, 0.165], pink: [1, 0.753, 0.796], lime: [0, 1, 0],
  navy: [0, 0, 0.502], teal: [0, 0.502, 0.502], maroon: [0.502, 0, 0],
  silver: [0.753, 0.753, 0.753], aqua: [0, 1, 1], fuchsia: [1, 0, 1],
};

const FALLBACK: RGB = [0.7, 0.7, 0.7];

function parseColor(stroke: unknown): RGB {
  if (typeof stroke !== 'string') return FALLBACK;
  const s = stroke.trim().toLowerCase();
  if (s in NAMED_COLORS) return NAMED_COLORS[s];
  if (s.startsWith('#')) {
    const h = s.slice(1);
    if (h.length === 6) {
      return [
        parseInt(h.slice(0, 2), 16) / 255,
        parseInt(h.slice(2, 4), 16) / 255,
        parseInt(h.slice(4, 6), 16) / 255,
      ];
    }
    if (h.length === 3) {
      return [
        parseInt(h[0] + h[0], 16) / 255,
        parseInt(h[1] + h[1], 16) / 255,
        parseInt(h[2] + h[2], 16) / 255,
      ];
    }
  }
  return FALLBACK;
}

self.onmessage = (e: MessageEvent) => {
  if (e.data.type !== 'parse') return;

  let fc: {features: unknown[]};
  try {
    const text = new TextDecoder().decode(e.data.buffer as ArrayBuffer);
    fc = JSON.parse(text) as {features: unknown[]};
  } catch (err) {
    self.postMessage({error: String(err)});
    return;
  }

  // First pass: compute centroid for ENU origin
  let lonSum = 0, latSum = 0, coordCount = 0;
  for (const feat of fc.features) {
    const g = (feat as {geometry: {type: string; coordinates: unknown}}).geometry;
    const coords = coordsOf(g);
    for (const c of coords) {
      lonSum += (c as number[])[0];
      latSum += (c as number[])[1];
      coordCount++;
    }
  }
  if (coordCount === 0) {
    self.postMessage({error: 'No coordinates found'});
    return;
  }

  const lon0 = lonSum / coordCount;
  const lat0 = latSum / coordCount;
  const cosLat = Math.cos(lat0 * Math.PI / 180);

  function toENU(lon: number, lat: number, depthKm: number): Vec3 {
    return [
      (lon - lon0) * 111320 * cosLat,
      (lat - lat0) * 110540,
      -depthKm * 1000,
    ];
  }

  // Second pass: build position + color buffers
  const positions: number[] = [];
  const colors: number[] = [];

  let minX = Infinity, maxX = -Infinity;
  let minY = Infinity, maxY = -Infinity;
  let minZ = Infinity, maxZ = -Infinity;

  function emit(p: Vec3, c: RGB) {
    positions.push(p[0], p[1], p[2]);
    colors.push(c[0], c[1], c[2]);
    if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0];
    if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1];
    if (p[2] < minZ) minZ = p[2]; if (p[2] > maxZ) maxZ = p[2];
  }

  for (const feat of fc.features) {
    const f = feat as {geometry: {type: string; coordinates: unknown}; properties?: Record<string, unknown>};
    const color = parseColor(f.properties?.['stroke']);
    const g = f.geometry;

    if (g.type === 'LineString') {
      const raw = g.coordinates as number[][];
      // Closed triangles: 4 coords where first === last
      if (raw.length === 4) {
        for (let i = 0; i < 3; i++) {
          emit(toENU(raw[i][0], raw[i][1], raw[i][2] ?? 0), color);
        }
      } else if (raw.length >= 3) {
        // Treat as open ring
        const ring: Vec3[] = raw.map(c => toENU(c[0], c[1], c[2] ?? 0));
        for (const idx of triangulateRing(ring)) {
          emit(ring[idx], color);
        }
      }
    } else if (g.type === 'Polygon') {
      const rings = g.coordinates as number[][][];
      if (rings.length > 0) {
        const ring: Vec3[] = rings[0].map(c => toENU(c[0], c[1], c[2] ?? 0));
        for (const idx of triangulateRing(ring)) {
          emit(ring[idx], color);
        }
      }
    } else if (g.type === 'MultiPolygon') {
      for (const poly of g.coordinates as number[][][][]) {
        if (poly.length > 0) {
          const ring: Vec3[] = poly[0].map(c => toENU(c[0], c[1], c[2] ?? 0));
          for (const idx of triangulateRing(ring)) {
            emit(ring[idx], color);
          }
        }
      }
    }
  }

  const posArr = new Float32Array(positions);
  const colArr = new Float32Array(colors);

  self.postMessage(
    {
      positions: posArr.buffer,
      colors: colArr.buffer,
      vertexCount: posArr.length / 3,
      bounds: {minX, maxX, minY, maxY, minZ, maxZ},
      origin: {lon0, lat0},
    },
    {transfer: [posArr.buffer, colArr.buffer]},
  );
};

function coordsOf(g: {type: string; coordinates: unknown}): number[][] {
  if (g.type === 'LineString') return g.coordinates as number[][];
  if (g.type === 'Polygon') return (g.coordinates as number[][][])[0] ?? [];
  if (g.type === 'MultiPolygon') {
    return ((g.coordinates as number[][][][])[0]?.[0]) ?? [];
  }
  return [];
}
