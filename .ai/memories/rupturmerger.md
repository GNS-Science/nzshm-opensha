# RuptureMerger Data Flow

## Mermaid Diagram

```mermaid
graph TD
    subgraph Inputs
        NR[nucleationRuptures<br/>e.g. subduction]
        TR[targetRuptures<br/>e.g. crustal]
        RS[FaultSystemRupSet]
    end

    subgraph "RuptureMerger (orchestrator)"
        RM[RuptureMerger]
        BJ[buildJumpLookup]
        GT[getJumpTargets]
        MJ[makeJump]
        FC[filter chain]
        MG[merge result]
    end

    subgraph "Spatial Indexing"
        RPL[RuptureProximityLookup]
        SPI[SectionProximityIndex]
        S2R["sectionToRupture Map"]
    end

    subgraph "Target Selection"
        TS[TargetRuptureSelector]
        ASS[AreaSpreadSelector]
    end

    subgraph "Compatibility Filters"
        MCF[MultiRuptureCompatibilityFilter]
        FRAC[FractCoulombPositiveFilter]
        NET[NetCoulombPositiveFilter]
        SELF[SelfStiffnessFilter]
        AREA[MinAreaFilter]
        SFRAC[SelfStiffnessFractionAreaFilter]
        SPREAD[SelfStiffnessSpreadFilter]
    end

    subgraph "Stiffness"
        SCM[StiffnessCalcModule]
        ASC[AggregatedStiffnessCalculator]
        CACHE[AggregatedStiffnessCache]
    end

    subgraph "Output"
        MRJ[MultiRuptureJump]
        MCR[MultiClusterRupture]
        CC[ConcurrentCounter]
    end

    subgraph "Reporting"
        MSP[MultiRuptureStiffnessPlot]
        MRP[MultiRupturePlot]
        RPG[RupturePlotPageGenerator]
    end

    RS --> RM
    NR --> RM
    TR --> RM

    RM --> BJ
    BJ --> RPL
    RPL --> SPI
    RPL --> S2R

    RM --> GT
    GT -->|nearby targets| S2R
    GT -->|filter candidates| TS
    TS -.->|impl| ASS

    GT -->|selected targets| MJ
    MJ -->|creates| MRJ

    MRJ --> FC
    FC --> MCF
    MCF -.->|impl| FRAC
    MCF -.->|impl| NET
    MCF -.->|impl| SELF
    MCF -.->|impl| AREA
    MCF -.->|impl| SFRAC
    MCF -.->|impl| SPREAD

    FRAC & NET & SELF & SFRAC & SPREAD --> ASC
    ASC --> CACHE
    SCM --> ASC

    FC -->|PlausibilityResult.isPass| MG
    MG -->|takeSplayJump| MCR

    RM --> CC

    MCR --> MSP
    SCM --> MSP
    MSP --> MRP
    MSP --> RPG
```

## Summary

For each nucleation rupture, `RuptureMerger`:
1. Finds nearby targets via spatial index (`RuptureProximityLookup` → `SectionProximityIndex`)
2. Reduces candidates with `TargetRuptureSelector` (e.g. `AreaSpreadSelector`)
3. Creates `MultiRuptureJump` per candidate pair
4. Runs compatibility filter pipeline (Coulomb stiffness checks) — early exit on FAIL_HARD_STOP
5. On pass, creates `MultiClusterRupture` via `takeSplayJump`

Runs in parallel across all nucleation ruptures.

## Key Classes

| Class | Role |
|-------|------|
| `RuptureMerger` | Orchestrator |
| `RuptureProximityLookup` | Spatial index: section → nearby target ruptures |
| `TargetRuptureSelector` | Interface to filter/reduce target candidates |
| `AreaSpreadSelector` | Picks evenly-spaced ruptures by area |
| `MultiRuptureJump` | Bridge between two ClusterRuptures |
| `MultiClusterRupture` | Merged result (extends ClusterRupture) |
| `MultiRuptureCompatibilityFilter` | Filter interface |
| `StiffnessCalcModule` | Coulomb stiffness calculator + cache |
| `ConcurrentCounter` | Thread-safe progress counter |

## Filter Implementations (impl/)

| Filter | Mechanism |
|--------|-----------|
| `MultiRuptureCoulombFilter` | Base: Coulomb stress between ruptures |
| `FractCoulombPositiveFilter` | % of positive interactions ≥ threshold |
| `NetCoulombPositiveFilter` | Sum of all interactions ≥ threshold |
| `SelfStiffnessFilter` | Self-stiffness of combined rupture |
| `MinAreaFilter` | Minimum area for crustal/subduction |
| `SelfStiffnessFractionAreaFilter` | Fraction of sections with positive stiffness |
| `SelfStiffnessSpreadFilter` | Spread + threshold combined check |

## Reporting (report/)

| Class | Role |
|-------|------|
| `MultiRuptureStiffnessPlot` | Generates stiffness analysis visualizations |
| `MultiRupturePlot` | Per-rupture colored fault diagram |
| `RupturePlotPageGenerator` | Per-rupture HTML pages with multiple plots |
