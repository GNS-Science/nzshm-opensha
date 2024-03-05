# nzshm-opensha
[NZ NSHM Programme](https://www.gns.cri.nz/research-projects/national-seismic-hazard-model/) opensha applications (patterned on the UCERF3 part of https://github.com/opensha/opensha)

## Priorities:

 - adapt the NZ Fault model from CFM NZ to suit the opensha tools
 - extend opensha to support subduction interface fault models. This is needed for the (in)famous 'Hikurangi'. 

## Getting started 

Pre-requirements: `git` and `jdk11`

 ```bash
git clone https://github.com/GNS-Scienc/opensha.git &&\
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

### and build ....

 ```bash
 .\gradlew build
 ```

### or test just this code

```bash
 .\gradlew localTests --info
```
 
Test reports are found at  `./build/reports/tests/localTests/index.html`

### or test everything (slow....)
```bash
 .\gradlew test
```

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


