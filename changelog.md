# Changelog

This changelog began in 2024.

Changes that might affect reproducibility are tagged with `behaviour change`.

## TBD

### Changed

- ⚠️`[behaviour change crustal inversion]` Slip rate scaling is now done [based on domain](https://github.com/GNS-Science/nzshm-opensha/pull/345) instead of polygon region. Fewer sections are now determined to be in the TVZ.
- ⚠️`[behaviour change subduction rupture set]` Subduction ruptures now have a [connectedness constraint](https://github.com/GNS-Science/nzshm-opensha/pull/302) to prevent disconnected ruptures being generated. 