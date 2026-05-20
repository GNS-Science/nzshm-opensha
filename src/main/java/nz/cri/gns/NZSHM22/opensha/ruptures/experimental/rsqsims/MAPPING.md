# RSQSim → OpenSHA rupture mapping

Maps RSQSim events (collections of triangular *patches*) to OpenSHA ruptures
(collections of polygonal *fault sections*). Done in two steps:

1. Patch → FaultSection mapping (`RsqSimPatchLoader`).
2. Event → Rupture: an event's sections are the union of sections of its patches, filtered by area fill ratio (`RsqSimEventLoader.toFaultSections`, `>0.5` fill).

There are two supported source flavours, with different input files and different mapping mechanisms: **Bruce** (`rundir5883`/`rundir5942`) and **Canterbury**.

---

## Common inputs (both flavours)

All inputs live under a single `basePath` directory.

### Patch geometry file
Format: ASCII, one patch per line (blank lines and `#` comments skipped). Line N (1-based, after skipping) defines patch with `id = N`. Each line has ≥11 whitespace-separated columns:

| col | meaning |
|-----|---------|
| 0–2 | x1, y1, z1 — first vertex (easting, northing, depth in metres) |
| 3–5 | x2, y2, z2 — second vertex |
| 6–8 | x3, y3, z3 — third vertex |
| 9   | rake (deg, Aki & Richards) |
| 10  | slip rate (m/s) |
| 11+ | ignored |

Coordinates are in a projected (orthographic, metres) system — UTM 59S for Bruce, NZTM (EPSG:2193) for Canterbury. `z` is depth and is converted to km via `abs(z)/1000`. See `PatchesFile.loadPatch` and `CoordinateConverter`.

Triangle area is computed from the planar vertices (cross product, /2). Used downstream for the fill-ratio test.

### Event catalogue files
Two little-endian int32 binary files in `basePath`:

- `eList` / `.eList` / `whole_nz.eList` — event ids (one per patch occurrence).
- `pList` / `.pList` / `whole_nz.pList` — patch ids (1-based, must reference an existing patch).

Same length. Consecutive rows with the same `eList` value belong to the same event; the event's patch list is the corresponding `pList` values. See `RsqSimEventLoader.loadEvents`.

### Rupture set
Hardcoded path in `RsqSimPatchLoader.getBrucePatches` / `getCanterburyPatches`: an OpenSHA `FaultSystemRupSet` ZIP (the "combined" NZSHM22 rup set — crustal + Hikurangi + Puysegur).

Crustal sections occupy ids `0..2324`, Puysegur `2325..2595`, Hikurangi `2596..` in the combined set (offsets used by Canterbury loader).

---

## Bruce flavour (`RsqSimPatchLoader.loadRupSetNewBruce`)

Files in `basePath`:

- `zfault_Deepen.in` — patch geometry (UTM 59S).
- `znames_Deepen.in` — one line per patch (same order as `zfault_Deepen.in`), bracketed text. The loader strips 4 chars from the front and 3 from the end (i.e. surrounding quote/bracket characters) and splits the remainder on space.

Each znames line is one of:

- A single token naming a subduction zone: exactly `"Hikurangi"` or `"Puysegar"` (note Puysegur spelling). The patch is mapped geometrically (see below).
- Two or more tokens, the first being an integer **OpenSHA section id** (in the combined rup set's id space). The patch is mapped directly to that section.

Mapping mechanism:

- **Crustal patches**: znames first token = section id → `loadedRupSet.getFaultSectionData(id)` is attached to the patch. No geometric check beyond the depth filter below.
- **Subduction patches** (zname is `Hikurangi` or `Puysegar`): every Hikurangi/Puysegur section in the rup set is tested for 2D containment of the patch's vertices (`Area.contains` on lon/lat path). If any of the 3 vertices falls inside the section polygon, that section is attached. A patch may match multiple subduction sections.

### Depth filter (`addSectionToPatch`)
For crustal sections (section name does **not** contain `"row:"`) where `aveDip > 20°`, a patch is rejected if **none** of its 3 vertices have depth within `[origAveUpperDepth, aveLowerDepth]`. Subduction patches and shallow-dip crustal sections skip this check.

### Bruce file requirements summary
- `zfault_Deepen.in`: patch geometry in UTM 59S, ≥11 cols, units metres.
- `znames_Deepen.in`: one line per patch, same order as patches; format `XXXX<content>YYY` where front 4 and back 3 chars are stripped; content is either `Hikurangi` / `Puysegar` or `"<sectionId> <name…>"`.
- `eList`, `pList`: matched length, little-endian int32.
- The section ids embedded in znames must be valid indices into the supplied rup set.

---

## Canterbury flavour (`RsqSimPatchLoader.loadRupSetCanterbury`)

Files in `basePath`:

- `whole_nz_faults_2500_tapered_slip.flt` — patch geometry (NZTM).
- `whole_nz.eList`, `whole_nz.pList` — catalogue.
- Three JSON mapping files, one per tectonic domain:
  - `rsqsim_crustal_discretized_trimmed_dict.json` (section offset `0`)
  - `puysegur_discretized_trimmed_dict.json` (section offset `2325`)
  - `hikkerm_discretized_trimmed_dict.json` (section offset `2596`)

Each JSON file is a `{ "<sectionId>": [patchId, patchId, …], … }` map. Keys are section ids in **that domain's** rup set (crustal-only, Puysegur-only, or Hikurangi-only); the loader adds the appropriate offset to lift them into the combined rup set's id space. Patch ids are 1-based and must point at an existing loaded patch (`patches.get(patchId - 1)`, with an `id == patchId` assertion). See `UCMappingsFile`.

No znames file is read. No geometric matching is performed. Section ids come entirely from the JSON files.

The same depth filter in `addSectionToPatch` applies.

### Canterbury file requirements summary
- `.flt` patch file: same format as Bruce's `zfault_Deepen.in`, but coordinates in NZTM (EPSG:2193).
- Three JSON dicts: keys = string-encoded section ids within domain; values = patch id arrays.
- Section offsets baked into the loader assume the standard combined rup set layout (2325 crustal, 271 Puysegur, then Hikurangi).
- `whole_nz.eList`, `whole_nz.pList`: same binary format as Bruce.

---

## Attributes used for the mapping

| attribute / field | source | used for |
|---|---|---|
| patch line index (1-based) | `zfault_Deepen.in` / `.flt` order | patch `id`; referenced by `pList` and Canterbury JSON |
| 3 vertices (x,y,z) | patch file cols 0–8 | lon/lat conversion, area, depth filter, subduction containment test (Bruce) |
| rake, slip | patch file cols 9–10 | stored on `Patch`, not used for mapping |
| znames first token (Bruce) | `znames_Deepen.in` | crustal section id (direct lookup) |
| znames keyword (Bruce) | `znames_Deepen.in` | tags patch as `Hikurangi`/`Puysegar` → triggers geometric subduction match |
| section name prefix | rup set sections | `startsWith("Hikurangi"|"Puysegur")` selects subduction candidates; `contains("row:")` distinguishes subduction sections downstream |
| section dip, upper/lower depth | rup set | depth-range filter for crustal patches |
| section polygon (perimeter) | rup set | 2D `Area.contains` for subduction matching (Bruce) |
| section id (with offset) | Canterbury JSON keys | direct lookup; offsets `0` / `2596` / `2325` |
| patch id | Canterbury JSON values | direct lookup into loaded patches |
| eList / pList | binary catalogue | grouping patches into events |
| triangle area vs section area | computed | event→section fill ratio (>0.5) in `RsqSimEventLoader.toFaultSections` |

---

## Notes & gotchas

- Bruce znames spelling: `Puysegar` in znames, but section names use `Puysegur`. The constants in `RsqSimPatchLoader` (`RSQSIMS_PUYSEGUR = "Puysegar"`) follow the znames spelling.
- znames parsing slices `line.substring(4, line.length()-3)` — assumes the file's exact bracketing/quoting; deviation will silently corrupt section ids.
- Subduction matching is 2D only (ignores depth). Bruce's subduction sections are coarse, so the depth filter is intentionally skipped for them.
- Canterbury section offsets are hardcoded and assume the specific combined rup set layout in `nzshm22_complete_merged.zip`; verified by `checkRupSetMatches`.
- A patch may map to zero, one, or many sections. Zero-section patches are tolerated (just ignored when reconstructing events).
