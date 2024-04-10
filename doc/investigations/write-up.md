# Examine compute cost and for combined rupture sets

Originally [Examine compute cost and for combined rupture sets based on jump / coupling distance](https://github.com/GNS-Science/nzshm-opensha/issues/313)

For exploring combined crustal/subduction rupture sets, we're looking at all of Puysegur and a part of the Alpine fault:

![Screenshot 2024-04-08 094253.png](Screenshot%202024-04-08%20094253.png)

Building this rupture set seemed to take an unproportional amount of time (> 50 minutes). 

## Opensha Performance Improvements

As part of this investigation, we submitted two low-hanging fruit performance improvements to the opensha repo. This reduced the rupture build time to 25 minutes.

- [SectIDRange memory usage improvements](https://github.com/opensha/opensha/pull/105)
- [Rupture building peformance improvements](https://github.com/opensha/opensha/pull/104)

## Fault intersection

One theory was that the intersecting crustal and subduction faults would lead to higher computational cost.

By keeping the same input but artificially lowering the subduction faults, we can observe performance changes:

"cru -> cru" is the number of possible jumps between crustal clusters. "cru -> sub" is the number of possible jumps from crustal to subduction clusters. Note the significant speed-up when there are no possible jumps to subduction. 

| extra depth | runtime    | ruptures | cru -> cru | cru -> sub |
|-------------|------------|----------|------------|------------|
| 0           | 25 minutes | 37,721   | 9          | 8          |
| 10 km       | 12 minutes | 16,605   | 9          | 3          |
| 20 km       | 11 seconds | 15,986   | 9          | 0          |

The algorithm to determine the jumps that are considered for rupture building is in `PlausibleClusterConnectionStrategy.checkBuildThreaded()` and takes a significant portion of the overall runtime. It uses Coulomb filters to determine if a jump might lead to plausible ruptures. It seems that in this case, the number of possible jumps was dominated by the max jump distance (5km)

## A closer look at possible jumps

The measurement results suggest that the number of possible cru->sub jumps has a significant influence on the total number of ruptures and the runtime. At 0 extra depth (i.e. using unmodified Puysegur geometry), we have a jump to subduction for nearly every cru->cru jump. 

How much complexity is added by jumping to subduction? One important measurement here is the number of possible subduction ruptures that can start at the jump's target fault section. 

Here is a list of the number of ruptures that can start at the target fault section for each possible jump:

- cru -> cru: 3, 2, 2, 2, 5, 5, 10, 10, 6
- cru -> sub: 224, 234, 234, 196, 186, 215, 239, 251

This suggests that jumps to subduction are a couple of magnitudes more expensive to compute than jumps between crustal sections. 

A highly naive interpretation could be: Since there is roughly one jump to subduction (targeting 222 ruptures on average) per jump to crustal, we'd expect computation to take 222 times as long as before. In fact, 25 minutes is 136 times longer than 11 seconds (the run where we have 0 cru-sub jumps).

## Conclusion

I believe that the performance improvements plus the naive explanation discussed in the previous section should put our worries to rest for now.

Once we're able to run Puysegur and all crustal faults or even Puysegur, Hikurangi, and all crustal faults, then we might want to look again into exploding complexities.
