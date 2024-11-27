# Joint Ruptures With Coulomb Filters

We investigated creating joint ruptures with Coulomb stiffness. The Coulomb filters that we used for NZSHM22 are written for crustal ruptures and would be too computationally expensive to use for joint ruptures.

Kevin Milner suggested the approach used here, and he also implemented the first filter iteration.

Coulomb filters are based on the Coulomb 'stiffness' between subsections where a positive stiffness encourages slip on the target section(s) and negative stiffness discourages slip. 

The NZSHM22 Coulomb filters were a pass/fail filter based on Coulomb stiffness. For a rupture to pass, it is 'grown' from an initial subsection `A` by using Coulomb stiffness to test if `A` induces `B` to slip, then if `AB` induces `C` to slip, then if `ABC` induces `D` to slip, etc. If any of these interactions is negative, this particular path through the rupture is abandoned. A rupture passes the filter if we are able to grow a path through the whole rupture from any of the rupture's subsections.

For joint ruptures, Kevin suggested to shortcut the "growing" algorithm by taking a passing crustal rupture and a passing subduction rupture and testing the stiffness between these two ruptures as a single step:

```
For Coulomb stress change in our application, the question is "if slip
happens on this fault patch will it encourage or discourage slip on this
other fault patch?" Slip can be encouraged in 2 ways: it can increase
shear stress in the direction that the other fault wants to slip
(i.e., our slip pushed the other fault in the way that it wants to
slip thus encouraging it). It can also be encouraged by unclamping
the normal stress on the fault (reducing the frictional pressure
holding the fault in place and keeping it from slipping). Positive
ΔCFF means slip is encouraged on the other fault, negative discouraged.

For simple cases it does a good job of incorporating things like azimuth
change between faults (if they're in line and slip the same direction,
they're compatible), but it also does a good job of handling more complex
situations where the faults intersect at weird angles or have different
senses of slip (reverse -> strike slip, left lateral -> right lateral,
etc). Those complex cases would be hard to encode into simple rules in
terms of azimuth change.

My simplest initial idea is just to break down the ruptures into patches
separately (crustal patches and subduction patches), then using the
subduction patches as sources calculate ΔCFF from each subduction patch
to each crustal patch. Then look at the fraction of those interactions
that are positive, and keep the co-rupture if it's above some threshold
value. A pro of this method is that it's super simple, a con is that it
weights all interactions equally no matter how far they are. We could
instead sum the net ΔCFF across all interactions and keep it if it's net
positive, which would weight near interactions above far field
interactions. We might also want some combination of those filters.
```

## Code

Development happens in our [opensha fork](https://github.com/GNS-Science/opensha) in the branch [experiment/subduction-coulomb](https://github.com/GNS-Science/opensha/tree/experiment/subduction-coulomb) mostly in the package [org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture](https://github.com/GNS-Science/opensha/tree/experiment/subduction-coulomb/src/main/java/org/opensha/sha/earthquake/faultSysSolution/ruptures/multiRupture). 

Note that some code in this document lives in `nzshm-opensha`, for example classes to generate combined rupture sets.

The `main` class is `RuptureMerger` and the `impl` sub-package contains the Coulomb implementation. 

`RuptureMerger` uses a rupture set that contains all NZSHM22 ruptures. And then calls filters with all subduction/crustal rupture combinations. 

The combined rupture set has been created with [RuptureAccumulator.java](..%2F..%2F..%2Fsrc%2Fmain%2Fjava%2Fnz%2Fcri%2Fgns%2FNZSHM22%2Fopensha%2Fruptures%2Fexperimental%2FRuptureAccumulator.java)

A text files with one rupture id per line may be used to filter the source crustal and subduction ruptures to simulate a thinning algorithm. The `main` method in [ThinningSubduction.java](..%2F..%2F..%2Fsrc%2Fmain%2Fjava%2Fnz%2Fcri%2Fgns%2FNZSHM22%2Fopensha%2Fruptures%2Fexperimental%2FThinningSubduction.java) may be used to create such a file.

Subsections are divided into "patches": 2km x 2km tiles. Ideally, we use 1km x 1km patches but we have increased patch sizes during development. Coulomb Stiffness is calculated between these patches. Stiffness is aggregated to calculate the stiffness between subsections or groups of subsections.

- `MultiRuptureFractCoulombPositiveFilter` tests whether the number of all positive interactions is at least at a certain percentage value. Currently, the threshold is 0.75, and the filter is applied in both directions (i.e. crustal -> subduction and subduction -> crustal).
- `MultiRuptureNetCoulombPositiveFilter` tests whether the net interaction is positive. This filter is also applied in both directions.

When running `RuptureMerger`, it is advised to use and re-use a cache for the Coulomb stiffness. We're currently using one that Kevin supplied, but it is possible to create our own.

The [oakley/WIP](https://github.com/GNS-Science/opensha/tree/oakley/WIP) branch off of the `experiment/subduction-coulomb` branch adds debugging reporting to investigate the generated ruptures.

## RSQSims

With very little real world data available, it is not easy to find a ground truth for verifying the generated ruptures. As in [Enumerating Plausible Multifault Ruptures in Complex Fault Systems with Physical Constraints](https://pubs.geoscienceworld.org/ssa/bssa/article-abstract/112/4/1806/613987/Enumerating-Plausible-Multifault-Ruptures-in), we attempt to compare our ruptures to RSQSims output.  

The approach is to take a NZ EQ catalogue generated in RSQSims, identify joint ruptures with a single crustal rupture, and then see if our filters pass the rupture.

Some RSQSims file formats are described in https://zenodo.org/records/5534462 and Bruce Shaw has provided us with a newer catalogue. We're hoping to get a catalogue that matches the crustal geometry from NZSHM22.

There is code for extracting SRQSims geometry in [feature/rsqsims](https://github.com/GNS-Science/nzshm-opensha/tree/feature/rsqsims). There is also the beginning of matching between RSQSims patches and opensha subsections. 

Hopefully we will be able to get a new RSQSims catalogue with matching geometry where we can use the subsection ids from the `znames_Deepen.in` file to map directly to subsections. 

The `main` method is in [RsqSimsLoader.java](https://github.com/GNS-Science/nzshm-opensha/blob/feature/rsqsims/src/main/java/nz/cri/gns/NZSHM22/opensha/ruptures/experimental/rsqsims/RsqSimsLoader.java). The code generates `geojson` files to verify the geometry.

When loading the patch geometry, one needs to know the [UTM zone](https://www.dmap.co.uk/utmworld.htm). The documentation in https://zenodo.org/records/5534462 says it's 11S, but for the NZ data I have seen it is actually 59. Rendering the geojson file will make it very obvious if the zone has been chosen correctly.

![Screenshot 2024-07-29 162713.png](Screenshot%202024-07-29%20162713.png)