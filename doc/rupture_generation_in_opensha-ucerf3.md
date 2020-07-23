## Rupture generation in opensha-ucerf3

July 23, 2020

Some notes on how this works currently, based on tests and insights from the experts.

## Background

We want to attempt Grand Inversion aproach to SRM for the Hikurangi subduction zone in NSHM-NZ. For UCERF3 the SCEC team
created a GI implementation in java (opensha-ucerf3) by extending their earlier opensha-* feature set.

GI is comprised of two main parts, Rupture Set generation and 'Grand Inversion'. This note is about the **Rupture Set generation**.

## What is a Rupture Set

Given enough information about a fault-system, including geometry (location, shape, depth) and historic data (prior ruptures, dates, magnitudes, ground displacement, etc) we want to 
produce a list of possible (plausible) earthquakes (or ruptures) for that fault system. This is a **RuptureSet**.

To provide enough data for hazard and risk analyis, we build ruptures from lists of fault sub-sections. Typically these sub-sections are less than 100km^2 in order to support a range of rupture magnitudes, by combining adjacent sub-sections into ruptures. (Smaller quakes come from smaller rupture areas).

*Part of the 'art' of this prediction lies in interpreting this information to produce a set of 'plausible ruptures'. What's plausible vs implausable? is a focus of this type of analsyis.*

## Implausible ruptures

Earthquakes involve one or more faults rupturing simultaneously. Evidence shows us that twhen multiple faults are involved, they're somehow connected. This may be a junction or intersection, or it may be through proximity e.g. two faults lying just a few km apart. 

  * It's unlikely for faults at significant distances to rupture simultaneously. => **MaxJumpDistance**
  * It's less likely for a fault with a given 'slip direction' to trigger faults having a significantly differnet 'slip direction'. => **MaxAzimuthChange**
  * Other implausibles....

For GI we want a wide range of plausilbe ruptures in the set, so we create many permutations of combined sub-sections, and then we filter them to eliminate implausible ruptures.

# UCERF3 Rupture Set logic

This is based on testing and code-analysis done in these repos:

 - https://github.com/opensha/opensha-commons.git
 - https://github.com/opensha/opensha-core.git
 - https://github.com/opensha/opensha-ucerf3.git
 - https://github.com/GNS-Science/nshm-nz-opensha.git

## Subduction zone testing

We've created a small subset of the Hikurangi fault surface in a set of (9 tiles)[https://github.com/GNS-Science/nshm-nz-opensha/blob/master/test/NSHM_NZ/inversion/fixtures/patch_4_10.csv].

From this we've created a rupture generator test to explore the rupture generator as it stands. 

The subduction zone fault is much larger than a typical surface fault, so it is divided into subsections in two axes, whereas surface faults are only divided longitudinally. This 2 axes relationship isn't supported in the codebase today, but there's still some testable functionality.

In (the test)[https://github.com/GNS-Science/nshm-nz-opensha/blob/d691b6bf1c89126ce6f787ec9681b19d4ed21f07/test/NSHM_NZ/inversion/InterfaceFaultSectionTest.java#L108] we see this:

### 1) Load the 9 tiles as list of subsections

```
...
		List<FaultSection> subductionSections = Lists.newArrayList();

		for (int row=1; row<csv.getNumRows(); row++) {
			fs = buildFaultSectionFromCsvRow(row-1, csv.getLine(row));
			subductionSections.add(fs);
		}
		
		// System.out.println(subductionSections.size() + " Subduction Fault Sections");

		String last_trace_name = "SubductionTile_5_11";
		assertEquals(last_trace_name, fs.getFaultTrace().getName());
		assertEquals(9, subductionSections.size());
...
```

Now we have 9 java FaultSection objects in an ArrayList container. Dumped to XML they look like :

```
...
<i0 sectionId="0" aveLongTermSlipRate="0.0" slipRateStdDev="0.0" aveDip="12.050192832946777" aveRake="0.0" aveUpperDepth="36.59041976928711" aveLowerDepth="38.678104400634766" aseismicSlipFactor="0.0" couplingCoeff="1.0" dipDirection="334.4695" parentSectionName="ParentSection 100" parentSectionId="100" connector="false" class="org.opensha.refFaultParamDb.vo.FaultSectionPrefData">
  <FaultTrace name="SubductionTile_3_9">
    <Location Latitude="-43.02716064453125" Longitude="172.05718994140625" Depth="36.59041976928711"/>
    <Location Latitude="-43.065799713134766" Longitude="171.94630432128906" Depth="36.59041976928711"/>
  </FaultTrace>
</i0>
...

```

### 2) Calculate distances between section centres

```
		// calculate distances between each subsection
		Map<IDPairing, Double> subSectionDistances = DeformationModelFetcher.calculateDistances(maxDistance, subSections);
```
We see that even in this very small dataset some distances are not calculated (sections 2 & 6). Is this our first 'implausible' rule in effect?...

**TODO what is the distance between subsections 2 & 6, and why is it NaN ??**

```
minDist Calc 2.861908287382075 :: 2 : 4
minDist Calc 0.4610826076290517 :: 2 : 5
minDist Calc NaN :: 2 : 6
minDist Calc 12.640588496225329 :: 2 : 7
minDist Calc 9.738284337026121 :: 2 : 8
```

then some more distance stuff...

```
		Map<IDPairing, Double> reversed = Maps.newHashMap();

		//Dump distances		
		for(IDPairing pair : subSectionDistances.keySet()) {
			System.out.println("pair: "+pair+ ";  dist: "+subSectionDistances.get(pair));
		}

		// now add the reverse distance
		for (IDPairing pair : subSectionDistances.keySet()) {
			IDPairing reverse = pair.getReversed();
			reversed.put(reverse, subSectionDistances.get(pair));
		}		
		subSectionDistances.putAll(reversed);
```


### 2) Calculate azimuths (angles) between section centres

I believe this is used for 

```
/**
 * This restricts the maximum azimuth change of any junction in the rupture. Azimuth
 * changes are computed as the azimuth change between the midpoints of two sections
 * on the same fault. For this reason, 2 sections are required per fault to compute
 * accurate azimuths.
 * 
 * @author kevin
 *
 */
public class AzimuthChangeFilter extends AbstractLaughTest {
```

```

		Map<IDPairing, Double> sectionAzimuths = DeformationModelFetcher.getSubSectionAzimuthMap(
				subSectionDistances.keySet(), subSections);
```
**TODO** check which azimuths are calculated 

### 2) Build a plausablity filter (LaughTest Filter)


```
		// instantiate our laugh test filter
		LaughTestFilter laughTest = LaughTestFilter.getDefault();

		// laughTest.setMaxCmlmJumpDist(5d); 	// has no effect here as it's a junction only test
		// laughTest.setMaxJumpDist(2d); 		// looks like this might only impact (parent) section jumps
		laughTest.setMinNumSectInRup(9); 	    // works!

		//disable our coulomb filter as it uses a data file specific to SCEC subsections
		CoulombRates coulombRates  = null;
		laughTest.setCoulombFilter(null);
```

 - the **laughTest** object is the default filter (set of tests). 
 - we set setMinNumSectInRup here only to check effect on rupset size. 
   Here, the ruptureset size is  reduced > an order of magnitude,  compared to min size of 1.
 - coulomb rates models aren't available (yet?) for NZ, so we're ommitting them.


### Build the list of plausible ruptures

```
		// this separates the sub sections into clusters which are all within maxDist of each other and builds ruptures
		// fault model and deformation model here are needed by InversionFaultSystemRuptSet later, just to create a rup set
		// zip file
		FaultModels fm = null;
		SectionClusterList clusters = new SectionClusterList(
				fm, DeformationModels.GEOLOGIC, laughTest, coulombRates, subSections, subSectionDistances, sectionAzimuths);

		List<List<Integer>> ruptures = Lists.newArrayList();
		for (SectionCluster cluster : clusters) {
			System.out.println("cluster "+cluster);// + " : " + cluster.getNumRuptures());
			ruptures.addAll(cluster.getSectionIndicesForRuptures());
		}		
		System.out.println("Created "+ruptures.size()+" ruptures");
```

**TODO** some notes on the 'standard' plausability tests in the **default laughTest** as relevant to a single fault section . i.e.  **minSectionsInRupture**