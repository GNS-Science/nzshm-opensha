# nzshm-opensha

[![codecov](https://codecov.io/gh/GNS-Science/nzshm-opensha/branch/main/graphs/badge.svg)](https://codecov.io/github/GNS-Science/nzshm-opensha)

[NZ NSHM Programme](https://www.gns.cri.nz/research-projects/national-seismic-hazard-model/) opensha applications (patterned on the UCERF3 part of https://github.com/opensha/opensha)

## Getting started 

Pre-requirements: `git` and `jdk11`

 ```bash
git clone https://github.com/GNS-Science/opensha.git &&\
git clone https://github.com/GNS-Science/nzshm-opensha.git
 ```

You might need to check out the correct branch for the `opensha` project. The branch name will be in `gradle.yml` in the
`nzshm-opensha` project as the `ref` of the `Clone opensha` step. As of writing, this is
`fix/rup-normalization-2024`.

```bash
cd opensha
git checkout fix/rup-normalization
```

### Now you can jump into this project

 ```bash
 cd nzshm-opensha
 ```

### and build

 ```bash
 ./gradlew build
 ```

## Gradle Tasks

Gradle tasks are run with `./gradlew`. Run `./gradlew tasks` for a complete list of tasks.

- `build`: builds and tests the project.
- `fatJar`: builds a standalone jar that contains all dependencies.
- `test`: runs unit tests. The report can be found at [./build/reports/tests/test/index.html](./build/reports/tests/test/index.html)
   - Run as `./gradlew test -PopenshaTest` to also run `opensha` tests. 
- `integrationTest`: runs integration tests.
- `smokeTest`: runs Smoke Tests.
- `jacocoTestReport`: creates a test coverage report. The `test` task needs to be run beforehand for an accurate result. The report can be found in [build/reports/jacoco/test/html](build/reports/jacoco/test/html/index.html)
- `spotlessApply`: uses the [Spotless plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle) to format the source code to match the style guide. See below.
- `pmd`: runs the [PMD source code analyzer](https://docs.pmd-code.org/latest/pmd_rules_java.html) over the Java code.
- `currentVersion`: prints the version derived from git tags.
- `release`: tags and publishes a new version. See below.

## Java Style

This repo follows the [AOSP Java code style](https://source.android.com/docs/setup/contribute/code-style). 

There is a gradle task `spotlessCheck` to verify that style guidelines are followed, and a task `spotlessApply` for re-format all Java files to match the style.

For `IntelliJ`, install the [google-java-format](https://plugins.jetbrains.com/plugin/8527-google-java-format) plugin. In the plugin settings, enable the plugin for this project and choose the AOSP style.


### Run or Reproduce NZSHM22 Results

See [doc/how-to-run.md](doc/how-to-run.md)

### Logging to Jupyter Notebooks

See [jupyterLog/README.md](jupyterLog/README.md)

## Releases

Releases follow [semantic versioning](https://semver.org/), tagged in git as `release/v<major>.<minor>.<patch>`.
The tag is the only source of truth for the version; there is no version number checked into the repo.
Versioning is handled by the [axion-release](https://axion-release-plugin.readthedocs.io/) Gradle plugin.

### What the version numbers mean

Because this project produces scientific models, the version tracks **reproducibility**, not API compatibility:

- **major**: a behaviour change. The same inputs no longer produce the same outputs.
- **minor**: new models, features or CLI options. Existing configurations produce unchanged outputs.
- **patch**: bug fixes, performance and documentation. No output changes.

### Changelog

Every change that a user would notice goes into the `## TBD` section of [changelog.md](changelog.md) as part of the
PR that makes it. Mark anything affecting reproducibility with ⚠️ and a `` `[behaviour change ...]` `` tag; the release
build refuses to cut a minor or patch release when the section contains one.

The `release` task closes off `## TBD` as `## [<version>] - <date>` and opens a fresh empty one, so you don't
edit release headings by hand.

### Making a release

From an up-to-date, clean checkout of `main`:

```bash
./gradlew release                                    # patch: 1.2.3 -> 1.2.4
./gradlew release -Prelease.forceVersion=1.3.0       # minor or major
```

This rewrites `changelog.md`, commits it, creates the tag and pushes both to `origin`. Add `-Prelease.dryRun` to
see what it would do without changing anything.

### What Github does with the tag

- Builds a fatjar, but only if all tests pass.
- Uploads it to an S3 bucket as `nzshm-opensha-all-<version>.jar` (no `v`).
- Creates a Github release whose notes are the changelog section for that version, failing the build if there is none.
- The version is readable from inside the jar through `nz.cri.gns.NZSHM22.util.GitVersion`.

To verify a release worked, check the Github action for the tag. The "Upload to S3" step should end with
`upload: main/build/libs/nzshm-opensha-all-1.3.0.jar to s3://***/nzshm-opensha-all-1.3.0.jar`.

Free-text `release/<name>` tags still build and upload a jar, for one-off builds that aren't releases. They skip
the changelog check and don't create a Github release.


