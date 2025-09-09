## Poligonisation of spatial PDF gridded data

This happens in the `nz.cri.gns.NZSHM22.opensha.polygonise` package.

It appears that this was not used directly during an inversion, but that it was used to create a file that Kiran used 
to modify the DSM. 

See https://nshmrevisionproject.slack.com/archives/C02E5PXMB1D/p1651186074485649 for a discussion of some of the details.


### Inputs:
    STEPS : resolution of up-sampled grid
    EXPONENT

1. Up-sample pdf grid from 10 steps per degree to `STEPS` steps per degree.
    - Values for the new grid are assigned by the nearest neighbour from the original grid. No new gradients are introduced.
    - The grid is then normalised to 1
    - From now on we operate on the up-sampled grid unless noted otherwise
2. For each sub section:
    - For each grid point inside the sub section polygon.
        - polyWeight = distance_of_point_to_fault_trace / distance_of_trace_to_poly_border
            - This means that polyWeight is 1 if it's on the polygon border and 0 if on the fault trace.
            - These distances are calculated parallel to the direction of the fault dip.
        - d = polyWeight^`EXPONENT`
        - new_value_at_grid_point = old_value * d / sum_of_all_d_in_polygon
3. Where a grid point is in more than one polygon, we average over all new values
4. Grid points not in polygons are left unmodified.
5. Down-sample to 10-step grid resolution.
    - No averaging or normalisation is performed.
    - From now on we operate on the normal grid resolution again
6. Mmins are calculated per grid point:
    - Add up all mMin values from sub sections where the grid point is inside the polygon
        - mMin values are normalised based on how much the grid bin overlaps with the fault section polygon
    

## Usage
- confirmed:
  - NZSHM22_SpatialSeisPDF.NZSHM22_1346 (this is not what was used in NZSHM22)
  - don't kill normalisation in RegionalRupSetData
  - runner.setPolygonizer(4, "LINEAR", 40) (see CrustalInversionRunner in 0b4e5f9012f01c1d2b3dac36da92d068c82acfd4)
  - possibly? setPolyBufferSize(runner.getPolyBufferSize(), 3);

With the following settings, we can reproduce the locations and the old grid value in `NZSHM22_PolygonisedDistributedModel.csv`:

```java
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setSpatialSeisPDF(NZSHM22_SpatialSeisPDF.NZSHM22_1346);
        runner.setPolyBufferSize(runner.getPolyBufferSize(), 3);
        runner.setPolygonizer(4, "LINEAR", 40);
```