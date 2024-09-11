# How to Build Rupture Sets and Run Inversions From Python

It is possible to drive the NZSHM OpenSHA functionality from Python.

## Start the Gateway

Acquire a `nzshm-opensha-all.jar` file for this project. This can for example be done by following the instructions in the [readme file](../README.md) and then run
```shell
./gradlew fatjar
```
Start the gateway in a separate terminal
```shell
NZSHM22_APP_PORT=25333 java -Xmx10G -classpath nzshm-opensha-all.jar nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway
```

If the process runs out of memory, the `-Xmx10G` parameter can be changed to give Java more memory than 10G.

## Connect To the Gateway

Ensure the `py4j` package is installed in your Python environment, for example using `pip`:
```shell
pip install py4j
```

In your Python script, you can now connect to the gateway:

```python
from py4j.java_gateway import JavaGateway, GatewayParameters

initial_gateway_port=25333
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=initial_gateway_port))
```

The gateway port needs to be identical to the one in the previous step.

## Build a Rupture Set

This is how to generate a rupture set equivalent to the NZSHM22 Hikurangi set.

```python
builder = gateway.getSubductionRuptureSetBuilder()

builder.setFaultModel("SBD_0_3_HKR_LR_30")
builder.setDownDipAspectRatio(2.0, 5.0, 5)
builder.setDownDipMinFill(0.1)
builder.setScalingRelationship("TMG_SUB_2017")
builder.setSlipAlongRuptureModel("UNIFORM")

rupSet = builder.buildRuptureSet()
builder.writeRuptureSet("testSet.zip")
```

It is recommended to generate a report on the rupture set to verify that it is as expected.

```python

report_generator = gateway.getReportPageGen()
report_generator
    .setName("Subduction Rupture Set")
    .setRuptureSet(rupSet)
    .setOutputPath("/tmp/subductionRuptureSetReport")
    .setPlotLevel("FULL")
    .setFillSurfaces(True)
    .generateRupSetPage()
```
