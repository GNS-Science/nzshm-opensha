import earcut from 'earcut';

type Vec3 = [number, number, number];

// Compute polygon normal using Newell's method (area-weighted, handles non-planar).
function newellNormal(ring: Vec3[]): Vec3 {
  let nx = 0, ny = 0, nz = 0;
  const n = ring.length;
  for (let i = 0; i < n; i++) {
    const [x0, y0, z0] = ring[i];
    const [x1, y1, z1] = ring[(i + 1) % n];
    nx += (y0 - y1) * (z0 + z1);
    ny += (z0 - z1) * (x0 + x1);
    nz += (x0 - x1) * (y0 + y1);
  }
  const len = Math.sqrt(nx * nx + ny * ny + nz * nz);
  if (len < 1e-10) return [0, 0, 1];
  return [nx / len, ny / len, nz / len];
}

function cross(a: Vec3, b: Vec3): Vec3 {
  return [a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]];
}

function normalize(v: Vec3): Vec3 {
  const len = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
  if (len < 1e-10) return [1, 0, 0];
  return [v[0]/len, v[1]/len, v[2]/len];
}

function dot(a: Vec3, b: Vec3): number {
  return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
}

// Returns flat index list into ring (each triple is a triangle).
export function triangulateRing(ring: Vec3[]): number[] {
  if (ring.length < 3) return [];
  if (ring.length === 3) return [0, 1, 2];

  const normal = newellNormal(ring);

  // Build orthonormal basis for the best-fit plane
  const pick: Vec3 = Math.abs(normal[2]) < 0.9 ? [0, 0, 1] : [1, 0, 0];
  const u = normalize(cross(normal, pick));
  const v = normalize(cross(normal, u));

  // Project ring to 2D
  const flat2D: number[] = [];
  for (const p of ring) {
    flat2D.push(dot(p, u), dot(p, v));
  }

  return earcut(flat2D, undefined, 2);
}
