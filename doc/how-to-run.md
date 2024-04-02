# How to Build Rupture Sets and Run Inversions

## Re-create NZHSM22 results 
Apart from using `runzi`, the easiest way to build rupture sets and run inversions is through the `nz.cri.gns.NZSHM22.opensha.util.ParameterRunner`. It offers a number of static methods to build rupture sets and run inversions using NZSHM22 parameters. See the `main()` method for a list of possible runs.

For example, a crustal rupture set can be built with `buildNZSHM22CoulombCrustalRupset()` and an inversion can be run with `runNZSHM22CrustalInversion()`.

`ParameterRunner` will by default use `/tmp/` as the input and output directory (`c:\tmp\` on Windows). You can change this behaviour by creating a tab-separated`.ParameterRunner.config` file that specifies different paths. This file is ignored by git so that each user can set their own paths:

```
outputPath  runs/output/
inputPath   runs/input/
```

Be careful when creating this file as some IDEs automatically replace tabs with spaces. 

## Running With Modified Parameters

`ParameterRunner` should not be modified for custom runs to minimise noise in `git`. 
We recommend creating a separate class with a `main()` method for custom runs and ignoring it in git. For example, Oakley uses `OakleyRunner`.

One simple approach is to copy one of the methods from `ParameterRunner` into the new class and modify the rupture set builder or inversion runner in the new code. For example:

```java
    public static FaultSystemSolution runNZSHM22CrustalInversion() throws IOException, DocumentException {
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCrustalInversionRunner(runner);
        
        //after the runner is set up, we can add custom configs
        runner.setMinMags(5.6, 5.6);
        // only run for 3 minutes
        runner.setInversionMinutes(3);
        
        FaultSystemSolution solution = runner.runInversion();
        parameterRunner.saveSolution(solution);
        return solution;
    }
```

This custom runner class can then also be used to call `NZSHM22_ReportPageGen` in order to create a rupture report or a solution report. 

## Reproducing Historic Runs From Toshi

In order to reproduce a historic runs from Toshi:

- Open the `DETAIL` tab in Toshi, for example http://simple-toshi-ui.s3-website-ap-southeast-2.amazonaws.com/InversionSolution/SW52ZXJzaW9uU29sdXRpb246NjMzMzY3Mw==/InversionSolutionDetailTab
- Copy and paste everything from the table (apart from the header "Meta") into a text file.
- This text file should now have a tab-separated key and value pair on most lines. Be careful that your editor did not accidentally convert the tabs into spaces.
- You can add comment lines by having them start with `;`. It is recommended to put the Toshi link as a comment into the parameters file.
- For inversion parameter files, the `rupture_set` path will have to be changed to the path to the rupture set on the local computer.
- If the file cannot be found, the inversion runner will supply a link to download it.
- Use this file to set up the `ParameterRunner`, then run as normal:

```java
    public static void runCustom() throws IOException {
        Parameters parameters = Parameters.fromFile(new File("myCustomParams.txt"));
        ParameterRunner parameterRunner = new ParameterRunner(parameters);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCrustalInversionRunner(runner);
        FaultSystemSolution solution = runner.runInversion();
        parameterRunner.saveSolution(solution);
        return solution;
    }
```

