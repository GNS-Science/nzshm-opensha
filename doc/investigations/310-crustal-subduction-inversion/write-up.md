# Combined crustal/subduction inversions - what are the challenges?

## Overview

The main difference is in constraints and MFDs.

Crustal adds code complexity with unused regional split.

- Target MFDs
  - Significantly simpler for subduction
- Constraints
  - Subduction uses 4, 
  - Crustal uses 5, 
  - 1 is identical, some other similar
- Crustal uses more inputs
  - paleo rates and probabilities
  - distributed seismicity

## Questions

Question I had when comparing inversion runners:

- Do we want to try and use modified crustal MFDs and constraints for both?
- Do we specify target MFDs and constraints separately for crustal and subduction? And if so, how do we treat hybrid ruptures?
- Does it make sense to apply paleo and distributed seismicity to subduction ruptures?

## Target MFDs

### Subduction
Two identical MFDs are supplied: 
- totalTargetGR, only depends on rupset max mag
- targetOnFaultSupraSeisMFD = totalTargetGR

### Crustal
- totalTargetGR
- trulyOffFaultMFD
- totalSubSeismoOnFaultMFD
- targetOnFaultSupraSeisMFDs
- subSeismoOnFaultMFD_List
- uncertaintyMFD

## Constraints

### Subduction
- SlipRateInversionConstraint, normalised
- SlipRateInversionConstraint, unnormalised
- RupRateMinimizationConstraint
  - targets ruptures that are below the minMag of any of its sections
- MFDInversionConstraint
  - config.getMfdEqualityConstraints()

Others are implemented, but they are not used per NZSHM22 parameters

### Crustal

- NZSHM22_SlipRateInversionConstraintBuilder.buildUncertaintyConstraint()
- PaleoRateInversionConstraint
- PaleoRateInversionConstraint for PaleoRateSmoothness
- RupRateMinimizationConstraint
  - I think it's set up the same way as for subduction
- MFDInversionConstraint
  - config.getMfdUncertaintyWeightedConstraints()

## Crustal specific inputs

- Paleo Rates are "attached as constraints to nearest section"*
- Paleo Probabilities are location independent and modify rates constraints
- Distributed Seismicity
  - a grid of seismicities not attributed to specific faults
  - flows into `totalSubSeismoOnFaultMFD`, calculated in `getCharSubSeismoOnFaultMFD_forEachSection()`

## Running combined rupset with crustalrunner

10 minute run. Solution did not converge on target, is this because subduction and crustal are so different?

![Screenshot 2024-03-29 130626.png](Screenshot%202024-03-29%20130626.png)
