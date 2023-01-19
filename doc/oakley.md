## Overview

This repo is mainly used to generate grand inversion solutions that indicate the probability of a fault rupturing. It is a fork of [opensha](https://github.com/opensha/opensha) which contained the [UCERF3](http://wgcep.org/UCERF3.html) code when we started, and most of the UCERF4 code when we finished with NZSHM22.

The basic steps are

- Using a fault model, generate a set of ruptures across these faults.
- Run the inversion over the rupture set.
- It's possible to generate hazards based on the solution. However, in practice, GNS uses OpenQuake to generate hazards.

## Generating Ruptures

Ruptures are generated in `src/main/java/nz/cri/gns/NZSHM22/opensha/ruptures`

### Fault Model

A fault model is basically a long list of rectangular cuts into the ground. For crustal faults, the top of the cut is at ground level, and properties such as dip and dip direction determine the surface of the rectangular cut. Slip indicates how much the fault is moving over time.

We model crustal (surface) faults, and subduction faults. Crustal faults have an upper depth of 0 and go along the surface. Subduction faults are complex underground shapes that we break up into a (geometrically disconnected) net of flat rectangles. There is separate code to generate ruptures fur crustal and subduction faults.

Have a look at src/main/resources/faultModels for NZ community fault models.

### Ruptures

Quakes happen when two (as per NZSHM22) or more fault sections rupture together. The solution is run on ruptures rather faults, and so we generate all possible (or rather all plausible) ruptures before we run the inversion. The total surface area of a rupture can be used to calculate the quake's magnitude using a `ScalingRelationship`.

Example:
We have a crustal fault with the sections A, B, and C, three connected rectangles. Three ruptures are possible on this fault: AB, BC, and ABC. As of NZSHM22, a rupture needs at least two fault sections.

Ruptures can jump from one fault to another fault if the distance is not too far. There are several factors that can limit the size of ruptures. For example, it is expected that ruptures are fairly straight, and we would not generate a rupture that curves back on itself.

Limitations in NZSHM22:
- no splays (ruptures are a straight string of sections, they do not fork)
- no jumps between crustal and subduction faults

We have three ways to generate ruptures:
- `NZSHM22_AzimuthalRuptureSetBuilder` builds on the UCERF3 way of generating ruptures with plausibility filters preventing ruptures with too far jumps or too much of a curve. We put a bit of work into this, but ended up not using it for NZSHM22 because Kevin had by then developed the Coulomb filter far enough.
- `NZSHM22_CoulombRuptureSetBuilder` This is what we used in NZSHM22. I (Oakley) do not know enough to describe how they work.
- `NZSHM22_SubductionRuptureSetBuilder` This is what we used to generate subduction ruptures. The opensha code is not written for 2-dimensional nets of rectangles, and so we mostly wrote this ourselves. We have filers for the overall shape of ruptures on a subduction surface. For example, we want ruptures to be mostly rectangular, but still tolerate jagged edges at the bottom end of the subduction surface.

All three of the builders have a main method that can be used for experimentation and debugging. Sample values are already set in the main methods.

### Rupture Archives

The result of rupture generation is a [zip file](#containers). Some relevant files in it are:

- ruptures/fault_section.geojson: the fault sections
- indices.csv: each row is a rupture, each column is an index into fault_section.geojson
- properties.csv: each row has properties for a rupture, for example magnitude and area

## Generating Solutions

The solution to a grand inversion is a list of rates, one (or at most one) for each rupture. In reality, most ruptures will be excluded from the solution to find a better fit, and so only some ruptures will have a rate.

We have two ways of generating solutions:

- `NZSHM22_CrustalInversionRunner` runs a solution over a crustal rupture set
- `NZSHM22_SubductionInversionRunner` runs a solution over a subduction rupture set

A number of other models flow into the inversion as constraints, such as background seismicity or paleo rates:

### Paleo Rates

Prehistoric rates are attached as constraints to the fault section nearest to the lat/lon specified.

This is to ensure the solution fits better to prehistoric earthquake rates.

Paleo rates files can be found in `src/main/resources/paleoRates` and their code in `NZSHM22_PaleoRates`.

### Paleo Probability Model

Paleo probabilities files can be found in `src/main/resources/paleoRates` and their code in `NZSHM22_PaleoProbabilityModel`.

### Background Seismicity

Background seismicity is specified as a [grid](#grids) of locations in NZ with a certain seismicity attached to each node. The inversion is focussed on seismicity from ruptures, but there is seismicity that scientists have not been able to attribute to specific faults. This "background noise" is captured here. This data is mostly relevant to hazard, but it also flows into [MFDs](#mfds).

The seismicity of a location is mostly determined by a fault if it's close to that fault, and it's mostly determined by background if it's far from a fault. NZSHM22 uses [fault polygons](#polygons) to determine this.

Background seismicity files can be found in `src/main/resources/data/seismicityGrids` and are handled in `NZSHM22_SpatialSeisPDF`.

For a while, background seismicity was handled differently in the TVZ (Taupō Volcanic Zone). There's a whole bunch of code to do this, but in the end the scientists decided not to use that feature. On top of that, technicalities and inconsistencies arise when a rupture crosses the TVZ border. This feature is controlled with the `setEnableTvzMFDs()` method on the `NZSHM22_CrustalInversionRunner`. This feature is the reason why many parameters are split into TVZ and SansTVZ.

### MFDs

[Magnitude-frequency distributions](https://hazard.openquake.org/gem/methods/mfd/) are used to constrain the solution to fit a certain magnitude profile. For example, we want low magnitude ruptures to have larger rates than high magnitude ruptures.

The main code for this is in `NZSHM22_CrustalInversionTargetMFDs`. NZSHM22 uses MFDs that are based on Gutenberg-Richter. There are global MFDs as well as MFDs attached as constraints to individual fault sections. 

MFDs inside the TVZ were handled differently at some stage (see [background seismicity](#background-seismicity)), so there's legacy code for this still visible.

The actual measured MFDs of the solution are a major tool for scientists to judge the quality of a solution. See [reports](#reports).

### Polygons

## Hazard
## Grids

## Containers
## Reports