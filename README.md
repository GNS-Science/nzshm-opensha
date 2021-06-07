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




