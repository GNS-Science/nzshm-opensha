# nzshm-opensha
[NZ NSHM Programme](https://www.gns.cri.nz/research-projects/national-seismic-hazard-model/) opensha applications (patterned on the UCERF3 part of https://github.com/opensha/opensha)

## Getting started 

Pre-requirements: `git` and `jdk11`   

 ```bash
git clone https://github.com/GNS-Science/opensha.git &&/
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
- `spotlessApply`: formats the source code to match the style guide.
- `pmd`: runs the [PMD source code analyzer](https://docs.pmd-code.org/latest/pmd_rules_java.html) over the Java code.

## Java Style

This repo follows the [AOSP Java code style](https://source.android.com/docs/setup/contribute/code-style). 

There is a gradle task `spotlessCheck` to verify that style guidelines are followed, and a task `spotlessApply` for re-format all Java files to match the style.

For `IntelliJ`, install the [google-java-format](https://plugins.jetbrains.com/plugin/8527-google-java-format) plugin. In the plugin settings, enable the plugin for this project and choose the AOSP style.


### Run or Reproduce NZSHM22 Results

See [doc/how-to-run.md](doc/how-to-run.md)

### Releases on github

Github can build release fatjars and push them to S3.

- In order to trigger a build, tag a commit as `release/<my release version string>`.
- `<my release version string>` will be added to the jar's file name.
- It will also be available inside the jar through `nz.cri.gns.NZSHM22.util.GitVersion`.
- Release jars will only be created if all tests pass.
- Release jars will be uploaded to a specific S3 bucket.
- To verify that it's working, check that the github action for the tag has succeeded. Specifically the "Upload to S3" task should
  end with "upload: main/build/libs/nzshm-opensha-all-1.0.jar to s3://***/nzshm-opensha-all-1.0.jar" (if "1.0" is your
  release version string).


