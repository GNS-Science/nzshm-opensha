# README

This is a docker image and `docker-compose` file to support our opensha-* experimnents.

## Build 

`$> docker build . -t nshm-nz/opensha`
    
## Option 1) Run Interactive

First, for all internal demos create a running container

`$> docker run -it --rm nshm-nz/opensha bash`
 
Now you're in the container, running the interactive bash shell

### 1.1) HazardCurveCalculator 

```
bash-4.4# java -cp $OPENSHA_PY_CLASSPATH org.opensha.py.HazardCurveCalcGateway &
bash-4.4# python3 opensha-py/src/test_gateway.py
```    

## Option 2) Run from bash 

This uses the `docker-compose.yml` file to define some standard volumne mappings.

### 2.1) UCERF3 Rupture set generator

From the docker host

```
docker-compose run --user $(id -u) opensha bash -c 'java -cp $OPENSHA_PY_CLASSPATH ./src/ucerf3/StandaloneSubSectRupGenMG.java'
```

**NB** `--user $(id -u):$(id -g)` sets the userid that your docker is run with, and which will be applied to any files created. 

see also: 

 - https://medium.com/@mccode/understanding-how-uid-and-gid-work-in-docker-containers-c37a01d01cf
 
