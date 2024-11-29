## Overview

This is the New Zealand implementation of the [OpenSHA](https://github.com/opensha/opensha) grand inversion to calculate the probability of a fault rupturing.

The basic steps are
 
- Using a fault model, generate a set of ruptures across these faults.
- Run the inversion over the rupture set.
- It's possible to generate hazards based on the solution. However, in practice, GNS uses [OpenQuake](https://github.com/gem/oq-engine) to generate hazards.

## OpenSHA

When we started, OpenSHA was pretty much the hardcoded implementation of [UCERF3](http://wgcep.org/UCERF3.html), and we were building an NZ implementation on top of this mostly by using inheritance. This was quite limiting, and the current maintainer [Kevin Milner](https://kevinmilner.net/) made a great effort to make the OpenSHA code more generic in order to support our implementation. 

Examples of this are [containers](#containers-and-archive-files) and scaling relationships. Initially, the choice of a scaling relationship was implemented as an enum. Enums cannot be extended, making it hard for us to make modifications on our side. Now, the scaling relationship required by the OpenSHA library is an interface, making it very easy for us to pass in our own.

OpenSHA is currently the UCERF4 implementation, but it is also a generic library that can be used for other NSHMs, such as NZSHM22. The general idea is that if we have any changes to OpenSHA that other models might be interested in, we created an OpenSHA pull request. If we have NZ-specific changes, these go into nzshm-opensha.

We also have a [fork of OpenSHA](https://github.com/GNS-Science/opensha), which we use to make changes to OpenSHA before turning them into pull requests. 

For a long time, we had weekly meetings with Kevin to coordinate our efforts, and he has been very helpful. I don't think we could have done it without him. 

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

We have two ways to generate ruptures:
- `NZSHM22_CoulombRuptureSetBuilder` This is what we used in NZSHM22. TODO: how do they work?
- `NZSHM22_SubductionRuptureSetBuilder` This is what we used to generate subduction ruptures. The OpenSHA code is not written for 2-dimensional nets of rectangles, and so we mostly wrote this ourselves. We have filters for the overall shape of ruptures on a subduction surface. For example, we want ruptures to be mostly rectangular, but still tolerate jagged edges at the bottom end of the subduction surface.

All three of the builders have a main method that can be used for experimentation and debugging. Sample values are already set in the main methods.

### Rupture Archives

The result of rupture generation is a [zip file](#containers-and-archive-files). Some relevant files in it are:

- ruptures/fault_section.geojson: the fault sections
- indices.csv: each row is a rupture, each column is an index into fault_section.geojson
- properties.csv: each row has properties for a rupture, for example magnitude and area

## Generating Solutions

The solution to a grand inversion is a list of rates, one (or at most one) for each rupture. In reality, most ruptures will be excluded from the solution to find a better fit, and so only some ruptures will have a rate.

We have two ways of generating solutions:

- `NZSHM22_CrustalInversionRunner` runs a solution over a crustal rupture set
- `NZSHM22_SubductionInversionRunner` runs a solution over a subduction rupture set

A number of other models flow into the inversion as constraints, such as background seismicity or paleo rates:

### Slip Rates

Slip rates (mm/year) are defined per fault segment in the fault models. Fault slips "manifest as earthquakes" ([OpenSha](https://opensha.org/Glossary.html#fault))

### Paleo Rates

Prehistoric rates are attached as constraints to the fault section nearest to the lat/lon specified.

This is to ensure the solution fits better to prehistoric earthquake rates.

Paleo rates files can be found in `src/main/resources/paleoRates` and their code in `NZSHM22_PaleoRates` (Enum).

### Paleo Probability Model

Paleo probabilities files can be found in `src/main/resources/paleoRates` and their code in `NZSHM22_PaleoProbabilityModel` (Enum).

### Distributed Seismicity

Distributed or background seismicity is specified as a [grid](#grids) of locations in NZ with a certain seismicity attached to each node. The inversion is focussed on seismicity from ruptures, but there is seismicity that scientists have not been able to attribute to specific faults. This "background noise" is captured here. This data is mostly relevant to hazard, but it also flows into [MFDs](#mfds).

The seismicity of a location is mostly determined by a fault if it's close to that fault, and it's mostly determined by background if it's far from a fault. NZSHM22 uses [fault polygons](#polygons) to determine this.

Background seismicity files can be found in `src/main/resources/data/seismicityGrids` and are handled in `NZSHM22_SpatialSeisPDF`.

For a while, background seismicity was handled differently in the TVZ (Taupō Volcanic Zone). There's a whole bunch of code to do this, but in the end the scientists decided not to use that feature. On top of that, technicalities and inconsistencies arise when a rupture crosses the TVZ border. This feature is controlled with the `setEnableTvzMFDs()` method on the `NZSHM22_CrustalInversionRunner`. This feature is the reason why many parameters are split into TVZ and SansTVZ.

### MFDs

[Magnitude-frequency distributions](https://hazard.openquake.org/gem/methods/mfd/) are used to constrain the solution to fit a certain magnitude profile. For example, we want low magnitude ruptures to have larger rates than high magnitude ruptures.

The main code for this is in `NZSHM22_CrustalInversionTargetMFDs` and `NZSHM22_SubductionInversionTargetMFDs`. NZSHM22 uses MFDs that are based on Gutenberg-Richter. There are global MFDs as well as MFDs attached as constraints to individual fault sections. 

MFDs inside the TVZ were handled differently at some stage (see [distributed seismicity](#distributed-seismicity)), so there's legacy code for this still visible.

The actual measured MFDs of the solution are a major tool for scientists to judge the quality of a solution. See [reports](#reports).

### Polygons

Fault polygons are mostly relevant for [background seismicity pdfs](#background-seismicity). The polygon of a fault section basically describes the area of influence it has. 

The simplest polygon is the fault section surface vertically projected onto the ground. This polygon is only ever on one side of the fault trace, and there's a UCERF3 algorithm to add a smaller polygon on the other side of the trace. NZSHM22 also ensures there is a minimum polygon x km wide on bth sides of the trace. This happens in `nz.cri.gns.NZSHM22.opensha.griddedSeismicity.SectionPolygons.applyBuffer()`

`SectionPolygons` is, like several other classes, copied from OpenSHA and then modified for NZ purposes. We tried to avoid doing this as much as possible, but sometimes inheritance is not suitable for our purposes. On top of that, many of these classes are no longer relevant for UCERF4 and are no longer maintained or are even deleted.

As mentioned [above](#background-seismicity), polygons are used to determine the area of influence of a fault vs background seismicity. UCERF3 assumes that in the polygon, there's only the fault seismicity, and outside, there's only background. NZSHM22 uses the `NZSHM22_PolygonisedDistributedModelBuilder` to attenuate background and fault influence gradually across the polygons. This flows both into the MFD constraints as well as into a re-built background seismicity pdf file, which is then fed into the OpenQuake hazard calculation. See `NZSHM22_PolygonisedDistributedModel` for how it is written into the archive container. 

*TODO: CDC: insert link to paper*

### Grids

Some model data is gridded, such as background seismicity. As with UCERF3, grids are anchored at lat/lon 0 and each node is 0.1 degrees apart from its neighbours. Grids are stored as CSV files.  For the [polygonizer](#polygons), we can scale grid resolution up and down.

The NZSHM22 implementation of grids is in `NZSHM22_GriddedData`.

### Geometry

OpenSHA uses `java.awt.geom` for geometric operations. For historic reasons, this cannot easily be changed (reproducibility of old results). AWT is not meant for this type of application, and OpenSHA sometimes runs into problems - especially when merging polygons. There is some code to try and retry in different ways.

The polygonizer does not need to respect reproducibility and uses the OpenMap library for geometric operations.

### LogicTrees

Logic trees are used by scientists to describe to combined choices they made when running an inversion. Logic trees are implemented in OpenSHA in order to configure a group of inversion runs. However, NZSHM22 only uses logic tree branches (LTBs), i.e. the configuration for a single inversion run. The whole tree is realised outside the Java code in [runzi](https://github.com/GNS-Science/nzshm-runzi). 

The structure of and the parameters of the scientists' logic tree branches do not necessarily match up with logic tree branches as modelled in this code. In this repo, NZSHM22 LTBs were implemented purely from a programmer's perspective without understanding how scientists think about them.

The logic tree branch is attached to the rupture set container and is thus available to each stage of the inversion preparation and the actual inversion. It's a handy way of passing the configuration to parts of the code.

The NZSHM22 implementations of `LogicTreeNode` are mainly found in the `enumTreeBranches` package. Most of these are enums. Originally, the UCERF3 enum tree nodes caused problems for us because we needed additional options, and enums cannot be extended. This caused huge problems for us for serialisation and for running the inversion before Kevin made OpenSHA more generic and less California-specific.

### Levels of Inversion Configuration

At the moment, we have several levels of configuration and inputs when running an inversion:

- The runners [NZSHM22_CrustalInversionRunner](../src/main/java/nz/cri/gns/NZSHM22/opensha/inversion/NZSHM22_CrustalInversionRunner.java) and [NZSHM22_SubductionInversionRunner](../src/main/java/nz/cri/gns/NZSHM22/opensha/inversion/NZSHM22_SubductionInversionRunner.java) provide methods to configure an inversion run, and they also hold the plain configuration values in their member variables. Some of these are LogicTreeNodes.
- A part of the configuration gets put into the logic tree branch and serialised in the resulting archive.
- The runner creates an `AbstractInversionConfiguration` instance that is a combination of input configuration and generated constraints.
- This configuration then gets used by an `InversionInputGenerator` which makes inversion constraints available to the inversion process.

## Hazard

Hazard calculation still works, but since NZSHM22 used OpenQuake for hazard calculation, it is obsolete.

Hazard takes the result of the inversion, the background seismicity, and additional models and calculates different hazards at certain locations. It can for example answer the question, "what is the probability if a ground motion movement exceeding X in the next 50 years at this location?" 

## Containers and Archive Files

Rupture sets and inversion solutions are stored as module containers that are saved as zip files on disk. New types of data can be added as modules to the containers and are serialised and deserialised appropriately. 

This modular system was conceived by Kevin in response to the inflexible structure of the UCERF3 archives that made it hard for NZSHM22-specific features to be implemented. The old format was binary with hard-coded serialisation and deserialisation. The modular format is agnostic of which modules need to be in the container and how they are written into the archive. It also provides an inheritance mechanism to fall back on more generic classes if the specific module class cannot be found.

In the archive file, containers that are added to a container are listed in `modules.json`. A module can be a container itself. Ruptures and solutions are examples of this, and they are usually found in the folders `/ruptures` and `/solution`. Most modules are serialised as json or CSV files. There are custom NZSHM22 modules such as `NZSHM22_PolygonisedDistributedModel`, `RegionSections`, and `NZSHM22_CrustalInversionTargetMFDs`.

Rupture generation and inversion create generic `FaultSystemRupSet` and `FaultSystemSolution` containers. However, in order to run the inversion, we unpack the rupture set and re-pack it as an `NZSHM22_InversionFaultSystemRuptSet`. This is because we add a few modules and also manipulate the existing data, such as running the [polygoniser](#polygons) over the [pdfs](#background-seismicity), and most importantly create the [MFDs](#mfds). 

On top of that, our inversion still extends the UCERF3 inversion, which historically relies on methods on the rupture set container that are not present in the generic container.

## Reports

OpenSHA provides extensive reporting functionality for rupture sets and inversion solutions. Use `NZSHM22_ReportPageGen` to generate reports. This report generator can be configured to include only certain types of report as some of the reports can take a long time.

## Python Gateway

As mentioned above, [logic trees](#logictrees) are actually run by [runzi](https://github.com/GNS-Science/nzshm-runzi), a Python application. This is done via the `NZSHM22_PythonGateway` which gives access to the different rupture generators, inversion runners, report builder, etc.

This can be tested via Java through the `PythonGatewayRunner`.

## Releases

Whenever a new nzshm-opensha release is required, tag the commit with `release/XXXX` where XXXX is a meaningful string that refers to an issue or a feature. GitHub will then automatically build a fat jar as specified in [gradle.yml](../.github/workflows/gradle.yml) and copy it to S3. The GitHub build logs will contain the file name, which should be posted to Slack.

## Future Work

- Setting up all the modules before running an inversion is brittle. They have to be set up in the correct order because they depend on each other implicitly, and some even have to be calculated twice. See for example [NZSHM22_InversionFaultSystemRuptSet.init()](../src/main/java/nz/cri/gns/NZSHM22/opensha/inversion/NZSHM22_InversionFaultSystemRuptSet.java) and related functions. It would be good to explicitly work out the dependencies of the modules, and maybe add a check at the beginning of module initialisation that the required data is available and correctly transformed.
- If hazard calculation is no longer required, could stripping it out make the code simpler?
- It would be great if [NZSHM22_InversionFaultSystemRuptSet](../src/main/java/nz/cri/gns/NZSHM22/opensha/inversion/NZSHM22_InversionFaultSystemRuptSet.java) was no longer required. Extending `InversionFaultSystemRupSet` is not a good idea. Historically, some special methods are required on the rupset to either set up everything for the inversion or to run the inversion, I cannot remember right now. The initialisation code would move to a new namespace.
- Synchronise [LTBs](#logictrees) with what scientists think they are.
- Add more tests
- Revisit the [inversion configuration](#levels-of-inversion-configuration) approach to see if all those levels are required.
- In order to debug or investigate weird solution runs, developers often need to locally run a runzi run. This means manually converting a runzi config into method calls to set up the runner. It would be very useful to have a more automated way to speed things up and reduce transcription errors.
- There is still a branch on our OpenSHA fork that should be merged or ported into the nzshm-opensha repo.

