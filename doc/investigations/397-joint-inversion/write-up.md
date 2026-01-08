# Experimental Joint Inversion

# Config

Goals:
- config is detangled from runners
- config is flexible, allowing some parameters to vary by partition (crustal, Hikurangi, Puysegur)
- config is stored as json in the solution zip file
- config can also be read as json
- eventually, config needs to be passed as json instead of set up programmatically

Problems:
- when removing setters and getters, how do we document the meaning and effect of inputs?
- how can we maintain flexibility if not every input is valid for each partition?
- runzi has extra logic to map from its config to opensha config. Makes it tricky to translate
    - example: reweight

# Logic Tree Branch

Currently, the code is not creating an LTB at all. 

# `FaultSectionProperties`

Rupture set module, contains fault section properties that we can not store through the usual 
mechanism for storing fault section data. Currently mainly used for partitioning the data. `main()` method can be used 
to backfill.

This is currently a flexible map of maps. We might implement prescriptive classes once the requirements are firmed up.  

## Required Properties

- `HIKURANGI` (boolean)
- `PUYSEGUR` (boolean)
- `CRUSTAL` (boolean)
- `TVZ` (boolean)
- `SANS_TVZ` (boolean)
- `origId` (number) if multiple fault models have been merged (e.g. for joint ruptures), then this value records the 
  original section id. This is used for matching up the deformation model.
- `origParent` (number) similar to `origId` but for the parent id.

# Modules

A number of modules need to be created before constraints are created.

- AveSlipModule
  - ave slip for ruptures
  - depends on scaling relationship
- SectSlipRates
  - potentially alternatives slip rates and stdevs as compared to data on sections

# Water Level

We have code to build a water level, but we never run it. The `minimumRuptureRateFraction` that is used to 
set the water level is always 0.

# Initial Solution

Probably can't use `variablePerturbationBasis` as the initial solution as this relies on a single set of MFDs. This was
only put in as an experimental option to help Chris R and was not used for NZSHM22.

Should be trivial to load an initial solution from file. Is not used for NZSHM22 and will not be implemented as part of 
this epic as it is not necessary to reproduce old results. 

# Constraints

Constraints are encoded in 
 - a matrix A where each row is a single constraint and each column is a rupture.
 - a vector d with a target for each row in the matrix A

For different partitions (crustal, Hikurangi, Puysegur), constraints may be set up differently. For example, crustal slip 
rate constraints have different parameters to subduction slip rate constraints. 

## Joint Constraints




Each slip rate constraint is set up for 
a single fault section. This means that we can use the constraint parameters for the partition of that fault section. 
Joint ruptures will have non-zero entries in crustal and subduction constraints.

We use `JointConstraintWrapper` to use existing constraint implementations to encode A and d, but filter encoding output
down to the currently encoded partition. 

Example: we have a joint rupture set with 2685 crustal and 452 hikurangi sections. We have a different slip rate constraint config 
for each of these partitions. We can use a regular `SlipRateInversionConstraint` instance to encode A and d for each
partition config, but each `SlipRateInversionConstraint` will encode constraints for all fault sections using one config. 
This means we get 2685 + 452 = 3137 rows encoded with the 
crustal config and 3137 rows encoded with the Hikurangi config. The `JointConstraintWrapper` then ensures that we only 
take the 2685 crustal rows that were encoded with the crustal config and the 452 Hikurangi rows that were encoded with
the Hikurangi config. We end up with 3137 rows in total.

The advantage of this is that we do not need to modify and maintain existing working and tested constraint implementations.

## Slip Rate Constraints

### Encoding

- might be multiple constraints depending on `slipRateWeightingType` and weights
- one row per fault section per constraint
- each constraint wrapped in `JointConstraintWrapper`

### Inputs

- `partition`
    - `deformationModel` (enum) slip rate and stdv in partition are overwritten, default: `FAULT_MODEL`
- `sansSlipRateFactor` (number) slip rate in crustal sans partition is multiplied by this
- `tvzSlipRateFactor` (number) slip rate in crustal TVZ partition is multiplied by this
- `partition`
  - `slipRateConstraintWt_normalized` (number)
  - `slipRateConstraintWt_unnormalized`: (number)
  - `slipRateWeightingType` (enum string)
    - `NORMALIZED`
    - `UNNORMALIZED`
    - `BOTH`
    - `NORMALIZED_BY_UNCERTAINTY`
  - `slipRateUncertaintyConstraintWt`: (number)
  - `slipRateUncertaintyConstraintScalingFactor`: (number)
  - `unmodifiedSlipRateStdvs`: (boolean)

### Dependencies

- AveSlipModule
- SlipAlongRuptureModel
- SectSlipRates
- area

## Paleo Constraints

### Encoding

- one row per paleo site
- will set values on all ruptures that contain the matching fault section
- paleo sites can only be matched with sections in the `CRUSTAL` partition
- if `paleoParentRateSmoothnessConstraintWeight` is not 0, also creates a `LaplacianSmoothingInversionConstraint`
  - one row per 3 adjacent sections that share the same parent
  - See section "Unresolved Questions"

### Inputs

- `paleoRateConstraintWt` (number)
- `paleoParentRateSmoothnessConstraintWeight` (number)
- `paleoRates` (enum string)
- `paleoProbabilityModel` (enum string)
- `extraPaleoRatesFile` (string)

### Dependencies
- magnitude

## `MFDInversionConstraint` (once target MFDs have been created)

### Encoding
- one row per bin in each MFD
- columns of ruptures that are in that bin are set to rate of that bin * modifiers
- if a rupture is only partially in the MFD's region, the rupture's rate is scaled proportionally


`MFDInversionConstraint` relies on rupset to get min and max mag and fraction of rupture in region. We could create a
new rupset class that returns those values based on a partition.  



# Unresolved Questions

- Scaling Relationship
- `LaplacianSmoothingInversionConstraint` works on adjacent triplets of fault sections with the same parent. 
  - How do we make this work with subduction faults? 
  - Do we limit this to crustal? 
  - Why is this in the paleo section?
- Do we not need to set `SlipAlongRuptureModel`? Do we always take what's in the rupture set?
- Annealing: NZSHM22 uses different parameters for crustal and subduction. I'm assuming we will want to use 
  the same settings for all regimes for joint ruptures.
