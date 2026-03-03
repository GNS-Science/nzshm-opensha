# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

in your chat answers, be extremely concise, sacrificing correct grammar.

## Project Overview

Java 11 / Gradle project implementing the New Zealand Seismic Hazard Model 2022 (NZSHM22), built on top of the OpenSHA framework. The upstream OpenSHA repo must be checked out at `../opensha` (branch `fix/rup-normalization-2024`).

## Commands

```bash
./gradlew build              # compile + unit tests + spotless check
./gradlew test               # unit tests only (NZSHM22 classes; opensha tests excluded by default)
./gradlew test --tests ClassName   # single test class
./gradlew test -PopenshaTest       # include upstream opensha tests (slow)
./gradlew integrationTest    # integration tests (src/integration/java/)
./gradlew smokeTest          # smoke tests (manual trigger)
./gradlew check              # all tests + PMD + spotless
./gradlew spotlessApply      # auto-format to Google AOSP style (run before committing)
./gradlew fatJar             # standalone jar with all dependencies
```

PMD violations are logged but do not fail the build (`ignoreFailures = true`).

## Architecture

### Package structure (`src/main/java/nz/cri/gns/NZSHM22/opensha/`)

| Package | Responsibility |
|---------|---------------|
| `enumTreeBranches/` | Logic tree branch enumerations: fault models, deformation models, scaling relations, regions |
| `faults/` | NZ-specific fault section definitions extending OpenSHA's `FaultSectionPrefData` |
| `calc/` | Magnitude-area and scaling relationships (TMG2017, Stirling 2021, etc.) |
| `analysis/` | Rupture set calculations (`NZSHM22_FaultSystemRupSetCalc`) |
| `data/region/` | NZ geographic boundaries (`NewZealandRegions`) |
| `ruptures/` | Rupture set building: down-dip and experimental joint ruptures |
| `griddedSeismicity/` | Off-fault gridded seismicity generation |
| `hazard/` | Hazard calculations (`NZSHM22_HazardCalculator`, `NZSHM22_HazardCalculatorBuilder`) |
| `inversion/` | **Core**: slip-rate inversion using simulated annealing |
| `inversion/joint/` | Joint crustal+subduction inversion; entry point is `InversionRunner` |
| `inversion/joint/constraints/` | Constraint generation and filtering; `ConstraintGenerator`, `FilteredFaultSystemRupSet` |
| `inversion/joint/reporting/` | Report plots extending OpenSHA's `AbstractRupSetPlot` |
| `util/` | Report page generation (`NZSHM22_ReportPageGen`), HPC runner (`OakleyRunner`) |

### Key entry points

- **`InversionRunner`** – run joint inversion from a JSON config file
- **`OakleyRunner`** – HPC/Slurm batch orchestrator

Config files live in `src/main/resources/parameters/` (`.jsonc` format).

### Relationship with `../opensha`

This project extends OpenSHA heavily:
- `FaultSystemRupSet` / `FaultSystemSolution` — rupture set and inversion solution containers
- `AbstractRupSetPlot` / `ReportPageGen` — report plotting framework
- `InversionConstraint` / `InversionInputGenerator` — constraint infrastructure
- `SolMFDPlot.initDefaultMFD()` — standard magnitude bin definition (use this for all MFD-related plots to stay consistent)

### Reporting plots

New plots go in `inversion/joint/reporting/`, extend `AbstractRupSetPlot`, output PNG via `PlotUtils.writePlots(...)`, and return markdown image links. `PartitionPlotWrapper` wraps existing plots to run separately per partition (crustal / subduction).

### Code style

Google Java Format, AOSP style. Run `./gradlew spotlessApply` before committing; CI runs `spotlessCheck` and fails on violations.
