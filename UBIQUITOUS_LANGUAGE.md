# Ubiquitous Language

## Seismic model structure

| Term | Definition | Aliases to avoid |
| ---- | ---------- | ---------------- |
| **Rupture Set** | A collection of all possible earthquake ruptures on a set of faults | Rup set |
| **Rupture** | A single hypothetical earthquake event defined by which fault sections break together | Event, earthquake |
| **Fault Section** | A discrete segment of a fault surface used as a building block for ruptures | Section, subsection, fault segment |
| **Fault** | A geological fracture along which displacement occurs | |
| **Solution** | The result of an inversion: a rupture set with assigned annual rates for each rupture | Inversion result |
| **Inversion** | The process of solving for rupture rates that satisfy slip-rate and MFD constraints via simulated annealing | |

## Partitions and tectonic domains

| Term | Definition | Aliases to avoid |
| ---- | ---------- | ---------------- |
| **Partition** | A tectonic grouping of fault sections (CRUSTAL, HIKURANGI, PUYSEGUR, TVZ, SANS_TVZ) | Region, domain, zone |
| **Crustal** | The shallow tectonic domain encompassing all non-subduction faults (TVZ + SANS_TVZ) | |
| **Subduction** | A tectonic domain where one plate descends beneath another (HIKURANGI or PUYSEGUR) | Interface |
| **Joint Rupture** | A rupture whose fault sections span more than one partition (e.g. crustal + subduction) | Shared rupture, multi-partition rupture |
| **Exclusive Rupture** | A rupture whose fault sections all belong to a single partition | Single-partition rupture |

## Rates and distributions

| Term | Definition | Aliases to avoid |
| ---- | ---------- | ---------------- |
| **MFD** | Magnitude-Frequency Distribution: the rate of earthquakes as a function of magnitude | Magnitude distribution |
| **Incremental MFD** | An MFD giving the rate within each discrete magnitude bin | |
| **Cumulative MFD** | An MFD giving the rate of earthquakes at or above each magnitude | |
| **Rate** | The expected annual frequency of occurrence of a rupture | Frequency, probability |
| **Slip Rate** | The long-term rate of displacement on a fault section, used as an inversion constraint | |

## Magnitude and area

| Term | Definition | Aliases to avoid |
| ---- | ---------- | ---------------- |
| **Magnitude** | A measure of earthquake energy release (moment magnitude scale) | Mag |
| **Scaling Relation** | A formula relating rupture area (or length) to magnitude | Mag-area relation |
| **Crustal Area Fraction** | The proportion of a joint rupture's total area contributed by crustal fault sections | Area ratio |

## Reporting and analysis

| Term | Definition | Aliases to avoid |
| ---- | ---------- | ---------------- |
| **Report Plot** | A class extending AbstractRupSetPlot that generates a PNG chart and returns markdown for inclusion in a report | Plot, chart |
| **Partition Plot Wrapper** | A decorator that runs an existing report plot once per partition | |
| **Comparison** | A second rupture set or solution shown alongside the primary one for side-by-side analysis | Reference model |

## Relationships

- A **Rupture Set** contains many **Ruptures**
- Each **Rupture** consists of one or more **Fault Sections**
- Each **Fault Section** belongs to exactly one **Partition**
- A **Joint Rupture** spans exactly two **Partitions** (one crustal, one subduction)
- An **Exclusive Rupture** belongs to exactly one **Partition**
- A **Solution** assigns a **Rate** to every **Rupture** in a **Rupture Set**
- An **MFD** is derived from a **Solution** by binning **Rates** by **Magnitude**
- A **Cumulative MFD** is derived from an **Incremental MFD**

## Example dialogue

> **Dev:** "When we compute the **Crustal Area Fraction** for a **Joint Rupture**, do we sum by **Fault Section** or use `getAreaForRup`?"

> **Domain expert:** "By **Fault Section**. Loop through each section in the **Rupture**, check which **Partition** it belongs to, and sum `getAreaForSection`. Then divide the crustal total by the overall total."

> **Dev:** "And an **Exclusive Rupture** wouldn't appear in this histogram at all?"

> **Domain expert:** "Correct. Only **Joint Ruptures** have a meaningful fraction -- they span both crustal and subduction **Partitions**. An **Exclusive Rupture** would be trivially 0 or 1."

> **Dev:** "For the **Cumulative MFD** chart, should we compute it from the **Incremental MFD** or directly from the **Solution**?"

> **Domain expert:** "From the **Incremental MFD**. Call `getCumRateDistWithOffset()` on each per-category **Incremental MFD**. That keeps the two charts consistent."

## Flagged ambiguities

- **"Region"** was not used in this conversation but appears in the codebase (`NewZealandRegions`, `calcNucleationMFD_forRegion`). A **Region** is a geographic boundary, distinct from a **Partition** which is a tectonic classification of fault sections. Do not conflate the two.
- **"Shared rupture"** appeared in `PartitionSummaryTable` field names (`sharedCounts`) as a synonym for **Joint Rupture**. Prefer **Joint Rupture** in domain discussions; the code uses "shared" internally but the report labels say "Joint".
- **"Area ratio"** vs **"Crustal Area Fraction"**: the plot file prefix uses `joint_area_ratio` but the concept is a fraction in [0, 1], not a ratio. Prefer **Crustal Area Fraction** in prose; the file name is a historical artifact.
