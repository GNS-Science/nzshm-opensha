# 3D GeoJSON Viewer

Electron desktop app for viewing large 3D GeoJSON datasets (fault patches, rupture surfaces, etc.).

## Requirements

Node.js 18+

## Run

```bash
cd 3dviewer
npm install
npm run dev
```

## Usage

- **Open file:** File → Open… or drag a `.geojson` file onto the window
- **Orbit:** left-click drag — orbits around the point you clicked on
- **Pan:** right-click drag
- **Zoom:** scroll wheel
- **Z-scale:** slider in toolbar (vertical exaggeration, default 1×)
- **Wireframe:** checkbox in toolbar

## Supported geometry

- `LineString` with 4 coords (closed triangle) — rendered directly
- `Polygon` / `MultiPolygon` — triangulated via Newell's method + earcut

Coordinates are assumed to be `[longitude, latitude, depth_km]` with depth positive downward. Features are coloured by the `stroke` property if present (named CSS colours and `#rrggbb`), otherwise light grey.
