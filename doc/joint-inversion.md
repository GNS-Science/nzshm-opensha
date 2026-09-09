# Joint Inversion Decisions

This is how joint inversions work currently.

See also [investigations/397-joint-inversion/constraints.md](investigations/397-joint-inversion/constraints.md), specifically
section "NZSHM22 Implementation" for how NZSHM22 constraints are constructed.

## Implementation for constraints that are applied per partition

- The rupture set is split into three different rupture sets (cru, hik, puy) using `FilteredFaultSystemRupSet`.
- Constraints are created for each of these filtered rupture sets. Since some ruptures are filtered out for each of these
  sets, `FilteredInversionConstraint` then translates and merges these smaller matrices into the large A matrix.
- `FilteredFaultSystemRupSet` will recalculate area and magnitude of the filtered ruptures, but will copy the slip from 
  the original ruptures.

## Slip Constraints

- As per NZSHM22, slip constraints are a section/rupture matrix.
- Joint ruptures have crustal constraints applied to their crustal sections and subduction constraints to their 
  subduction sections. This is the normal behaviour of `FilteredInversionConstraint`.
- When encoding constraints, the slip of the whole joint rupture is used for each partition. Rupture slip is not 
  re-calculated for the filtered part of the rupture, or scaled by the filtered part's proportional area. 

## MFD Constraints

- As per NZSHM22, MFD constraints are an MFD bin/rupture matrix.
- Joint ruptures have crustal constraints applied to their crustal bins and subduction constraints to their
  subduction bins. This is the normal behaviour of `FilteredInversionConstraint`.
- Note that since magnitude is re-calculated for the filtered rupture sets, crustal and subduction bins are likely
  different for a joint rupture.

## Global Constraints

- Paleo constraints are applied equally to all ruptures that contain a paleo section. No filtering is applied.
- The same goes for Laplacian smoothing, which is applied without filtering. 