# Constraint Implementation

This document describes how constraints were implemented for NZSHM22 and for our experimental joint inversion.

## General terms

Constraints are encoded in
- a matrix A where each row is a single constraint and each column is a rupture.
- a vector d with a target for each row in the matrix A

Note that the word "constraint" does double lifting here. For example, an MFD constraint is encoded as several rows in
the matrix.

"Partition": for the purposes of this document, joint rupture sets can be split into three partitions:
CRUSTAL, HIKURANGI, and PUYSEGUR. Each fault section is part of exactly one partition. We might want to configure
constraints differently for different partitions. A partition is similar to a region, but is based on attributes
rather than a polygon.

## NZSHM22 Implementation

### Slip Rate Constraints

Rows on A correspond to a fault section each. In each row, each rupture that has that particular fault section will be
set to a value based on the slip rate.

The matrix values are based on the slip on the section in the rupture (based on `calcSlipOnSectionsForRup()`).
The target is based on the section's slip rate.

```
              rup1 rup2 rup3 rup4    target
             ┌                  ┐     ┌ ┐
sectionId 1  | X    X           |     |X|
sectionId 2  |      X    X      |     |X|
sectionId 3  | X              X |     |X|
             └                  ┘     └ ┘
```
In this diagram, "X" denotes a non-zero value.

### MFD Constraints

Rows on A correspond to MFD bins. In each row, each rupture whose magnitude fits into that bin will be set to a value
based on that bin's rate. The target is also based on the bin's rate.

```
        rup1 rup2 rup3 rup4    target
       ┌                  ┐     ┌ ┐
bin 1  | X    X           |     |X|
bin 2  |           X      |     |X|
bin 3  |                X |     |X|
       └                  ┘     └ ┘
```

### Paleo Constraints

Each row on A corresponds to a paleo site and its matching fault section. Columns will be set to a value if the
corresponding rupture contains the fault section. Paleo constraints are only applied during crustal inversion.

Values in A are based on the paleo rate, modified by the paleo probability.
Values in d are only based on the paleo rate.

The encoded constraints have the same shape as slip rate constraints.

```
              rup1 rup2 rup3 rup4    target
             ┌                  ┐     ┌ ┐
sectionId 1  | X    X           |     |X|
sectionId 2  |      X    X      |     |X|
sectionId 3  | X              X |     |X|
             └                  ┘     └ ┘
```

### Other constraints

Other constraints used are
- `LaplacianSmoothingInversionConstraint`
- `RupRateMinimizationConstraint` (deprecated)
- `U3MFDSubSectNuclInversionConstraint`

These follow the two basic shapes (rows are MFD bins or fault sections). Might have to look closer into this to see
if we need to do anything special.


## Joint Inversion Decisions

This is how joint inversions work currently.

### Implementation for constraints that are applied per partition

- The rupture set is split into three different rupture sets (cru, hik, puy) using `FilteredFaultSystemRupSet`. For each 
  rupture, only the sections belonging to that partition will be included. This means joint ruptures will be partially 
  included. 
- Constraints are created for each of these filtered rupture sets. Since some ruptures are filtered out for each of these
  sets, `FilteredInversionConstraint` then translates and merges these smaller matrices into the large A matrix.
- `FilteredFaultSystemRupSet` will recalculate area and magnitude of the filtered ruptures, but will copy the slip from 
  the original ruptures. Since joint ruptures will now only have fault sections of that specific partition, area and
  magnitude will be different from before.
- Magnitude is calculated in `EstimatedJointScalingRelationship` as 
  `Math.log10(subductionArea * 1e-6 * Math.pow(10, 4.0) + crustalArea * 1e-6 * Math.pow(10, 4.2))`

### Slip Constraints

- As per NZSHM22, slip constraints are a section/rupture matrix.
- Joint ruptures have crustal constraints applied to their crustal sections and subduction constraints to their 
  subduction sections. This is the normal behaviour of `FilteredInversionConstraint`.
- When encoding constraints, the slip of the whole joint rupture is used for each partition. Rupture slip is not 
  re-calculated for the filtered part of the rupture, or scaled by the filtered part's proportional area. 

### MFD Constraints

- As per NZSHM22, MFD constraints are an MFD bin/rupture matrix.
- Joint ruptures have crustal constraints applied to their crustal bins and subduction constraints to their
  subduction bins. This is the normal behaviour of `FilteredInversionConstraint`.
- Note that since magnitude is re-calculated for the filtered rupture sets, crustal and subduction bins are likely
  different for a joint rupture.

### Global Constraints

- Paleo constraints are applied equally to all ruptures that contain a paleo section. No filtering is applied to the 
  ruptures, except that paleo sections must be crustal.
- The same goes for Laplacian smoothing, which is applied without filtering. 