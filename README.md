# nshm-nz-opensha
NSHM NZ Programme opensha applications (patterned on opensha-ucerf3 &amp; opensha-dev)

## Priorities:

 - adapt the NZ Fault model from CFM NZ to suit the opensha tools
 - extend opensha to support subduction interface fault models. This is needed for the (in)famous 'Hikurangi'. 

## Getting started 

 ```
git clone https://github.com/opensha/opensha.git &&\
git clone https://github.com/GNS-Science/nshm-nz-opensha.git
 ```

### Now you can jump into this project

 ```
 cd nshm-nz-opensha
 ```

### and build ....

 ```
 .\gradlew build
 ```

### or test just this code

```
 .\gradlew localTests --info
```
 
Test reports are found at  `./build/reports/tests/localTests/index.html`

### or test everything (slow....)
```
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


