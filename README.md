# nshm-nz-opensha
NSHM NZ Programme opensha applications (patterned on opensha-ucerf3 &amp; opensha-dev)

## Priorities:

 - adapt the NZ Fault model from CFM NZ to suit the opensha tools
 - extend opensha to support subduction interface fault models. This is needed for the (in)famous 'Hikurangi'. 

 ## Getting started 

 This project depends on three opensha repos...

 ```

git clone https://github.com/opensha/opensha-commons.git &&\
git clone https://github.com/opensha/opensha-core.git &&\
git clone https://github.com/opensha/opensha-ucerf3.git &&\
git clone https://github.com/GNS-Science/nshm-nz-opensha.git
 ```

### Now you can `cd` into this peoject

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

### or test all the dependencies

```
 .\gradlew test
```




