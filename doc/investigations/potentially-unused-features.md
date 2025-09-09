## Features unused by runzi

Most of these are probably useful to keep for experimenting.

- ` NZSHM22_AbstractInversionRunner.setExcludeRupturesBelowMinMag()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/152 
    - if set to true, ruptures are excluded (by sampler) if `rupSet.isRuptureBelowSectMinMag(r)` is true
    - if set to false, `NZSHM22_CrustalInversionConfiguration` will call `newConfig.setMinimizationConstraintWt(minimizationConstraintWt);`
- `NZSHM22_CrustalInversionRunner.setEnableMinMaxSampler()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/190 
    - if set to true, ruptures are excluded (by sampler) if `(mag < minMag_Sans || mag > maxMagSans)`
- `NZSHM22_AbstractInversionRunner.setVariablePerturbationBasis()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/296 
    - double array that can be used as variable perturbation basis. Optional, there is a default
    - becomes relevant if `setPerturbationFunction()` is set to a variable function
- `NZSHM22_AbstractInversionRunner.setInitialSolution()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/112
    - can use CSV file or opensha archive
- `NZSHM22_AbstractInversionRunner.setInversionAveragingIterations()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/141
    - part of reproducible inversions
    - runzi sets seconds instead
- `NZSHM22_AbstractInversionRunner.setPolyBufferSize()`
    - https://github.com/GNS-Science/nzshm-opensha/issues/150
    - default values are basically constants
- `NZSHM22_AbstractInversionRunner.setSyncInterval()`
    - deprecated, can be deleted. replaced by `setSelectionInterval()`

- `setInversionAveraging()`
- `NZSHM22_AbstractInversionRunner.setEnableInversionStateLogging()`
 
- A block of functions added for repeatable inversions and fine-grained inversion control
    - `setRepeatable()`
    - `setInversionMinutes()`
    - `setIterationCompletionCriteria()`
    - `setSelectionIterations()`
    - `setInversionAveragingIterations()`
