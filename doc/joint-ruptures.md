# Joint Ruptures

## Rupture Generation

Joint ruptures are generated from existing crustal and subduction ruptures. You can use `nz.cri.gns.NZSHM22.opensha.ruptures.experimental.Builder` to build a joint rupture set from crustal and subduction sets. Pass it a list of file paths to rupture sets.

If you use older (e.g. NZSHM22) sets, then you need to set the backfill flag to backfill new fault section properties.

The `Builder` will 

- backfill new properties
- accumulate the rupture sets into a new rupture set
- create an aggressive thinning filter for the rupture set
- create joint ruptures by combining crustal and subduction ruptures, and then add these to the rupture set


Most of these steps is configurable, but you'll have to modify the `Builder` code to make use of that.

Joint ruptures are filtered using Bruce's Coulomb filter and magnitudes are calculated using his approximate scaling relationship.

The builder might run for multiple hours.

## Inversion

To run a joint inversion, use `nz.cri.gns.NZSHM22.opensha.inversion.joint.InversionRunner`.

The `InversionRunner` takes all its inputs from a config file. See [parameters](../src/main/resources/parameters/) for sample `.jsonc` files. Or use the config file inside the solution folder of an existing joint solution.

## Reports

`NZSHM22_ReportPageGen` now has `setComparisonSolution()` which will turn the report into a comparison report. 

Reports now also have a `JointRuptureRatePlot` and rupture reports have a `PartitionSummaryTable`