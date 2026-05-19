const TILE_SIZE = 256;

interface Origin { lon0: number; lat0: number; }
interface Bounds { minX: number; maxX: number; minY: number; maxY: number; }

// Normalize any longitude to [-180, 180)
function normalizeLon(lon: number): number {
  return ((lon % 360) + 540) % 360 - 180;
}

function lonToTileX(lon: number, zoom: number): number {
  return Math.floor((normalizeLon(lon) + 180) / 360 * Math.pow(2, zoom));
}

function latToTileY(lat: number, zoom: number): number {
  const latRad = lat * Math.PI / 180;
  return Math.floor((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * Math.pow(2, zoom));
}

function tileToLon(tx: number, z: number): number {
  return tx / Math.pow(2, z) * 360 - 180;
}

function tileToLat(ty: number, z: number): number {
  return Math.atan(Math.sinh(Math.PI * (1 - 2 * ty / Math.pow(2, z)))) * 180 / Math.PI;
}

// Shift lon into the same ±180° window as ref (for correct ENU offset)
function adjustToFrame(lon: number, ref: number): number {
  while (lon - ref > 180) lon -= 360;
  while (ref - lon > 180) lon += 360;
  return lon;
}

function chooseZoom(spanMeters: number): number {
  if (spanMeters > 2_000_000) return 5;
  if (spanMeters > 1_000_000) return 6;
  if (spanMeters > 500_000) return 7;
  if (spanMeters > 200_000) return 8;
  if (spanMeters > 100_000) return 9;
  return 10;
}

export interface TileResult {
  canvas: HTMLCanvasElement;
  geoWest: number; geoEast: number; geoNorth: number; geoSouth: number;
}

export async function buildTileTexture(origin: Origin, bounds: Bounds): Promise<TileResult> {
  const cosLat = Math.cos(origin.lat0 * Math.PI / 180);

  const west  = origin.lon0 + bounds.minX / (111320 * cosLat);
  const east  = origin.lon0 + bounds.maxX / (111320 * cosLat);
  const south = Math.max(-85, origin.lat0 + bounds.minY / 110540);
  const north = Math.min(85,  origin.lat0 + bounds.maxY / 110540);

  const spanMeters = Math.max(bounds.maxX - bounds.minX, bounds.maxY - bounds.minY);
  const zoom = chooseZoom(spanMeters);
  const n = Math.pow(2, zoom);

  const tx0    = lonToTileX(west, zoom);
  const tx1raw = lonToTileX(east, zoom);
  // If east wrapped behind west in tile space, the region crosses the antimeridian
  const tx1 = tx1raw < tx0 ? tx1raw + n : tx1raw;

  const ty0 = latToTileY(north, zoom);
  const ty1 = latToTileY(south, zoom);

  const numX = tx1 - tx0 + 1;
  const numY = ty1 - ty0 + 1;

  const canvas = document.createElement('canvas');
  canvas.width  = numX * TILE_SIZE;
  canvas.height = numY * TILE_SIZE;
  const ctx = canvas.getContext('2d')!;

  const fetches: Promise<void>[] = [];
  for (let tx = tx0; tx <= tx1; tx++) {
    const actualTx = ((tx % n) + n) % n; // wrap around antimeridian
    const col = tx - tx0;
    for (let ty = ty0; ty <= ty1; ty++) {
      const row = ty - ty0;
      const url = `https://tile.openstreetmap.org/${zoom}/${actualTx}/${ty}.png`;
      fetches.push(
        fetch(url)
          .then(r => r.blob())
          .then(blob => new Promise<void>(resolve => {
            const img = new Image();
            const blobUrl = URL.createObjectURL(blob);
            img.onload = () => {
              ctx.drawImage(img, col * TILE_SIZE, row * TILE_SIZE);
              URL.revokeObjectURL(blobUrl);
              resolve();
            };
            img.onerror = () => { URL.revokeObjectURL(blobUrl); resolve(); };
            img.src = blobUrl;
          })),
      );
    }
  }
  await Promise.all(fetches);

  // Express geo bounds in the same longitude frame as origin.lon0 so ENU offsets are correct
  const geoWest = adjustToFrame(tileToLon(tx0, zoom), origin.lon0);
  const geoEast = geoWest + numX / n * 360;

  return {
    canvas,
    geoWest,
    geoEast,
    geoNorth: tileToLat(ty0,     zoom),
    geoSouth: tileToLat(ty1 + 1, zoom),
  };
}
