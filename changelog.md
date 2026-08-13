# Changelog

This changelog began in 2024.

Changes that might affect reproducibility are tagged with `behaviour change`, and make the next release a major
one. Add your entries to `## TBD`; see [Releases](README.md#releases) for the rest.

## TBD

## [2.0.0] - 2026-08-14

### Changed

- ⚠️`[behaviour change crustal inversion]` Subsection selection for slip rate scaling and target MFDs is now done [based on domain](https://github.com/GNS-Science/nzshm-opensha/pull/345) instead of polygon region. Fewer sections are now determined to be in the TVZ.
- ⚠️`[behaviour change subduction rupture set]` Subduction ruptures now have a [connectedness constraint](https://github.com/GNS-Science/nzshm-opensha/pull/302) to prevent disconnected ruptures being generated. 
- ⚠️`[behaviour change inversion]` Inversion selection completion criteria are now [applied together](https://github.com/GNS-Science/nzshm-opensha/pull/353). Same goes for average completion criteria.
