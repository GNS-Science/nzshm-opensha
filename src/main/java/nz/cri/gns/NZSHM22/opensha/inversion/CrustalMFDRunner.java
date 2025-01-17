package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.reports.NZSHM22_MFDPlot;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

public class CrustalMFDRunner {

    String outputPath = "TEST/mfd";
    String name = "MFD Plot";

    public CrustalMFDRunner setName(String name) {
        this.name = name;
        return this;
    }

    public CrustalMFDRunner setOutputPath(String path) {
        this.outputPath = path;
        return this;
    }

    /**
     * Creates MFD plots on a runner before an inversion is run. Good for a quick turnaround when
     * playing with MFD settings, does not include solution MFD.
     *
     * @param runner
     * @throws IOException
     * @throws DocumentException
     */
    public void runBeforeInversion(NZSHM22_CrustalInversionRunner runner)
            throws IOException, DocumentException {

        runner.configure();

        NZSHM22_MFDPlot plot = new NZSHM22_MFDPlot();
        RupSetMetadata rupMeta = new RupSetMetadata(name, runner.rupSet);
        ReportMetadata meta = new ReportMetadata(rupMeta);

        ReportPageGen rupReport = new ReportPageGen(meta, new File(outputPath), List.of(plot));
        rupReport.generatePage();
    }

    /**
     * Runs the specified runner and generates MFD plots on the solution. RegionalTargetMFDs don't
     * yet write all MFDs to file, so some curves can only be plotted like this.
     *
     * @param runner
     * @throws IOException
     * @throws DocumentException
     */
    public void runOnSolution(NZSHM22_CrustalInversionRunner runner)
            throws IOException, DocumentException {

        FaultSystemSolution solution = runner.runInversion();
        solution.write(new File(outputPath, "/solution.zip"));

        NZSHM22_MFDPlot plot = new NZSHM22_MFDPlot();
        RupSetMetadata rupMeta = new RupSetMetadata(name, solution.getRupSet(), solution);
        ReportMetadata meta = new ReportMetadata(rupMeta);

        ReportPageGen rupReport = new ReportPageGen(meta, new File(outputPath), List.of(plot));
        rupReport.generatePage();
    }

    /**
     * Plots MFDs for the specified solution file.
     *
     * @param solutionFile
     * @throws IOException
     */
    public void runOnSolution(String solutionFile) throws IOException {
        FaultSystemSolution solution = FaultSystemSolution.load(new File(solutionFile));
        NZSHM22_MFDPlot plot = new NZSHM22_MFDPlot();
        RupSetMetadata rupMeta = new RupSetMetadata(name, solution.getRupSet(), solution);
        ReportMetadata meta = new ReportMetadata(rupMeta);

        ReportPageGen rupReport = new ReportPageGen(meta, new File(outputPath), List.of(plot));
        rupReport.generatePage();
    }

    public static void main(String[] args) throws DocumentException, IOException {
        SimplifiedScalingRelationship scaling =
                (SimplifiedScalingRelationship)
                        NZSHM22_PythonGateway.getScalingRelationship(
                                "SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();

        runner.setMinMags(7.0, 6.5)
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85)
                .setPaleoRateConstraints(
                        1000.0, 100.0, "GEODETIC_SLIP_4FEB", "NZSHM22_C_42") // RUNZI updated [x]
                .setInversionSeconds(10)
                .setScalingRelationship(scaling, true)
                //   .setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(
                        "C:\\Users\\volkertj\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw==.zip")
                .setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setSlipRateConstraint("BOTH", 1000, 1000);

        CrustalMFDRunner mfdRunner = new CrustalMFDRunner();
        mfdRunner.setName("NZSHM22 MFD Plots").setOutputPath("TEST/mfd").runOnSolution(runner);
    }
}
