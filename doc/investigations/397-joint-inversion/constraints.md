# Joint Constraints

This document describes how inversion constraints are currently implemented, and how they could work for joint 
inversion.

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
Values in D are only based on the paleo rate.

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

## Joint Constraint Models

We want to be able to configure constraints differently for different partitions (crustal, hikurangi, puysegur). For
example with different weights.

The resulting encoded constraints must be identical to NZSHM22 if we only use fault sections from a single partition.

### Fault Section-Based

For constraints with rows corresponding to fault sections, each row is encoded according to the partition of that 
fault section.

Example: 
- slip rate constraint with weight 2 for CRUSTAL partition
- slip rate constraint with weight 4 for HIKURANGI partition
- sections 1 and 2 are in CRUSTAL, section 3 is in HIKURANGI

```
              rup1 rup2 rup3 rup4    target
             ┌                   ┐     ┌  ┐
sectionId 1  | 2X   2X           |     |2X|
sectionId 2  |      2X   2X      |     |2X|
sectionId 3  | 4X             4X |     |4X|
             └                   ┘     └  ┘
```

This model is currently implemented for slip rate constraints for joint inversions. 

### Rupture-Based

Each value in the A matrix is modified by how much the rupture is in the partition of the constraint.

Example:
- MFD constraint a for CRUSTAL partition
- MFD constraint b for HIKURANGI partition
- rupture 1 and 2 are 100% in CRUSTAL
- rupture 3 is 30% in CRUSTAL, 70% in HIKURANGI
- rupture 4 is 100% in HIKURANGI

```
          rup1 rup2 rup3 rup4    target
         ┌                   ┐     ┌ ┐
a bin 1  | 1X        0.3X    |     |X|
a bin 2  |      1X        0X |     |X|
b bin 1  | 0X        0.7X    |     |X|
b bin 2  |      0X        1X |     |X|
         └                   ┘     └ ┘
```

`MFDInversionConstraint` has this model built-in using regions. UCERF3 had separate MFDs for northern and 
southern California.

This model is currently implemented for MFD constraints for joint inversions.

### Merging

Instead of having separate MFD constraints for separate partitions, we can create MFDs separately and 
then merge (add) them to create joint MFD constraints.

This is the (unused) NZSHM22 implementation of MFD regions.

## Discussion

While the fault section based model is more efficient because it results in fewer rows, the rupture-based approach is 
more universal as it would work for every constraint. It might be valuable to be consistent in how we create joint
constraints.

This is what the slip rate constraint example would look like:

Example:
- slip rate constraint with weight 2 for CRUSTAL partition
- slip rate constraint with weight 4 for HIKURANGI partition
- rupture 1 is 50% in each partition
- ruptures 2 and 3 are 100% in CRUSTAL
- rupture 4 is 100% in HIKURANGI

```
              rup1     rup2 rup3 rup4     target
             ┌                       ┐     ┌  ┐
sectionId 1  | 2*0.5X   2X           |     |2X|
sectionId 2  |          2X   2X      |     |2X|
sectionId 3  | 2*0.5X             0X |     |2X|
sectionId 1  | 4*0.5X   0X           |     |4X|
sectionId 2  |          0X   0X      |     |4X|
sectionId 3  | 4*0.5X             4X |     |4X|
             └                       ┘     └  ┘
```

Note how each of the two constraints is encoded in 3 rows with their weight, modified by the fraction of the rupture 
inside the partition.

This approach would also allow us to treat subduction parts of ruptures differently for paleo rate constraints. We could
have different weight, or even different paleo probabilities (if it makes sense to do so).

