# nshm-nz-opensha
NSHM NZ Programme opensha applications (patterned on opensha-ucerf3 &amp; opensha-dev)

THis repo capture NHSM-NZ exploits based on opensha-ucerf3. 

Priorities:

 - adapt the NZ Fault model from CFM NZ to suit the opensha tools
 - extend opensha to support subduction interface models - needed for the infamous Hikurangi


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
 
 Test reports are found at  `file://$(pwd)/build/reports/tests/localTests/index.html`


### or test all the dependencies

```
 .\gradlew test
```


### open test outputs in your browwer




