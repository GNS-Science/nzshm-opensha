package nz.cri.gns.NZSHM22;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculator;
import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculatorBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway;
import org.dom4j.DocumentException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmokeTest {

    static File tempDir;

    @BeforeClass
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("opensha").toFile();
        System.out.println("smoke tests directory: " + tempDir);
    }

    @Test
    public void testAzimuthalCrustal() throws DocumentException, IOException {
        File dir = new File(tempDir, "azimuthal");
        dir.mkdir();
        File ruptureSetFile = new File(dir, "rupSet.zip");
        File solutionFile = new File(dir, "solution.zip");

        testAzimuthalRuptures(ruptureSetFile);
        testRupSetReportPageGen(ruptureSetFile);
        testCrustalInversionRunner(ruptureSetFile, solutionFile);
        testSolutionReportPageGen(solutionFile);
        testHazard(solutionFile, "INCLUDE");
    }

    @Test
    public void testCoulombCrustal() throws DocumentException, IOException {
        File dir = new File(tempDir, "coulomb");
        dir.mkdir();
        File ruptureSetFile = new File(dir, "rupSet.zip");
        File solutionFile = new File(dir, "solution.zip");

        testCoulombRuptures(ruptureSetFile);
        testRupSetReportPageGen(ruptureSetFile);
        testCrustalInversionRunner(ruptureSetFile, solutionFile);
        testSolutionReportPageGen(solutionFile);
        testHazard(solutionFile, "INCLUDE");
    }

    @Test
    public void testSubduction() throws DocumentException, IOException {
        File dir = new File(tempDir, "subduction");
        dir.mkdir();
        File ruptureSetFile = new File(dir, "rupSet.zip");
        File solutionFile = new File(dir, "solution.zip");

        testSubductionRuptures(ruptureSetFile);
        testRupSetReportPageGen(ruptureSetFile);
        testSubductionInversionRunner(ruptureSetFile, solutionFile);
        testSolutionReportPageGen(solutionFile);
        testHazard(solutionFile, "EXCLUDE");
    }

    public void sanityCheckAzimuthalRuptureSet(FaultSystemRupSet rupSet) {
        assertEquals(1278, rupSet.getNumRuptures());
        assertEquals(145, rupSet.getSlipRateForAllSections().length);
        assertEquals(145, rupSet.getSlipRateStdDevForAllSections().length);

        // sanity check first rupture
        assertEquals("Acton", rupSet.getFaultSectionData(0).getParentSectionName());
        assertEquals(2.0e-4, rupSet.getSlipRateForSection(0), 0.0000000001);
        assertEquals(1.5e-4, rupSet.getSlipRateStdDevForSection(0), 0.0000000001);
        assertEquals(6.890572888121233, rupSet.getMagForRup(0), 0.0000000001);
    }

    public void testAzimuthalRuptures(File file) throws DocumentException, IOException {

        NZSHM22_PythonGateway.NZSHM22_CachedAzimuthalRuptureSetBuilder builder = NZSHM22_PythonGateway.getAzimuthalRuptureSetBuilder();

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        FaultSystemRupSet rupSet = builder
                .setThinningFactor(0.2)
                .setMaxFaultSections(40)
                .setFaultModel(NZSHM22_FaultModels.CFM_0_9A_ALL_D90)
                .setScalingRelationship(scaling)
                .setSlipAlongRuptureModel(SlipAlongRuptureModels.TAPERED)
                .buildRuptureSet();

        builder.writeRuptureSet(file.getAbsolutePath());

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        branch.clearValue(NZSHM22_ScalingRelationshipNode.class); // don't recalculate mags
        NZSHM22_InversionFaultSystemRuptSet loadedRupSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(file, branch);

        sanityCheckAzimuthalRuptureSet(rupSet);
        sanityCheckAzimuthalRuptureSet(loadedRupSet);
    }

    public void sanityCheckCoulombRuptureSet(FaultSystemRupSet rupSet) {
        assertEquals(3064, rupSet.getNumRuptures());
        assertEquals(145, rupSet.getSlipRateForAllSections().length);
        assertEquals(145, rupSet.getSlipRateStdDevForAllSections().length);

        // sanity check first rupture
        assertEquals("Acton", rupSet.getFaultSectionData(0).getParentSectionName());
        assertEquals(2.0e-4, rupSet.getSlipRateForSection(0), 0.0000000001);
        assertEquals(1.5e-4, rupSet.getSlipRateStdDevForSection(0), 0.0000000001);
        assertEquals(6.890572888121233, rupSet.getMagForRup(0), 0.0000000001);
    }

    public void testCoulombRuptures(File ruptureSetFile) throws DocumentException, IOException {

        NZSHM22_PythonGateway.NZSHM22_CachedCoulombRuptureSetBuilder builder = NZSHM22_PythonGateway.getCoulombRuptureSetBuilder();

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        FaultSystemRupSet rupSet = builder
                .setAdaptiveMinDist(6.0d)
                .setMaxJumpDistance(15d)
                .setAdaptiveSectFract(0.1f)
                .setMaxFaultSections(40)
                .setFaultModel(NZSHM22_FaultModels.CFM_0_9A_ALL_D90)
                .setScalingRelationship(scaling)
                .setSlipAlongRuptureModel(SlipAlongRuptureModels.TAPERED)
                .buildRuptureSet();

        builder.writeRuptureSet(ruptureSetFile.getAbsolutePath());

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        branch.clearValue(NZSHM22_ScalingRelationshipNode.class); // don't recalculate mags
        NZSHM22_InversionFaultSystemRuptSet loadedRupSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(ruptureSetFile, branch);

        sanityCheckCoulombRuptureSet(rupSet);
        sanityCheckCoulombRuptureSet(loadedRupSet);
    }

    public void sanityCheckSubductionRuptureSet(FaultSystemRupSet rupSet) {
        assertEquals(100, rupSet.getNumRuptures());
        assertEquals(452, rupSet.getSlipRateForAllSections().length);
        assertEquals(452, rupSet.getSlipRateStdDevForAllSections().length);

        // sanity check first rupture
        assertEquals("Hikurangi, Kermadec to Louisville ridge, 30km - higher overall slip rates, aka Kermits revenge", rupSet.getFaultSectionData(0).getParentSectionName());
        assertEquals(1.749000046402216E-6, rupSet.getSlipRateForSection(0), 0.0000000001);
        assertEquals(0, rupSet.getSlipRateStdDevForSection(0), 0.0000000001);
        assertEquals(6.255087804186543, rupSet.getMagForRup(0), 0.0000000001);
    }

    public void testSubductionRuptures(File rupturesFile) throws DocumentException, IOException {

        NZSHM22_PythonGateway.NZSHM22_CachedSubductionRuptureSetBuilder builder = NZSHM22_PythonGateway.getSubductionRuptureSetBuilder();

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupSubduction(3);

        FaultSystemRupSet rupSet = builder
                .setMaxRuptures(100)
                .setDownDipAspectRatio(2, 5, 7)
                .setDownDipPositionCoarseness(0.0)
                .setDownDipSizeCoarseness(0.0)
                .setDownDipMinFill(0.5)
                .setMaxFaultSections(10)
                .setFaultModel(NZSHM22_FaultModels.SBD_0_4_HKR_LR_30)
                .setScalingRelationship(NZSHM22_PythonGateway.getScalingRelationship("TMG_SUB_2017"))
                .setScalingRelationship(scaling)
                .setSlipAlongRuptureModel(SlipAlongRuptureModels.UNIFORM)
                .buildRuptureSet();

        builder.writeRuptureSet(rupturesFile.getAbsolutePath());

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.subductionInversion();
        branch.clearValue(NZSHM22_ScalingRelationshipNode.class); // don't recalculate mags
        NZSHM22_InversionFaultSystemRuptSet loadedRupSet = NZSHM22_InversionFaultSystemRuptSet.loadSubductionRuptureSet(rupturesFile, branch);

        sanityCheckSubductionRuptureSet(rupSet);
        sanityCheckSubductionRuptureSet(loadedRupSet);
    }

    public void testCrustalInversionRunner(File ruptureSetFile, File solutionFile) throws DocumentException, IOException {

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        NZSHM22_PythonGateway.CachedCrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();

        FaultSystemSolution solution = runner
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85)
                .setInversionSeconds(1)
                .setSelectionInterval(1)
                .setScalingRelationship(scaling, true)
                .setRuptureSetFile(ruptureSetFile)
                //.setGutenbergRichterMFDWeights(100.0, 1000.0)
                //.setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setUncertaintyWeightedMFDWeights(0.5, 0.5, 0.5)
                .runInversion();

        runner.writeSolution(solutionFile.getAbsolutePath());

        FaultSystemSolution loadedSolution = FaultSystemSolution.load(solutionFile);

        assertEquals(solution.getRupSet().getNumRuptures(), loadedSolution.getRateForAllRups().length);

    }

    public void testSubductionInversionRunner(File ruptureSetFile, File solutionFile) throws DocumentException, IOException {

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupSubduction(3.0);

        NZSHM22_PythonGateway.CachedSubductionInversionRunner runner = NZSHM22_PythonGateway.getSubductionInversionRunner();

        FaultSystemSolution solution = runner
                .setGutenbergRichterMFD(29, 1.05, 8.85)
                .setInversionSeconds(1)
                .setSelectionInterval(1)
                .setScalingRelationship(scaling, true)
                .setRuptureSetFile(ruptureSetFile)
                //.setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setUncertaintyWeightedMFDWeights(1000, 0.1, 0.5)
                .setSlipRateConstraint("BOTH", 1000, 1000)
                .runInversion();

        runner.writeSolution(solutionFile.getAbsolutePath());

        FaultSystemSolution loadedSolution = FaultSystemSolution.load(solutionFile);

        assertEquals(solution.getRupSet().getNumRuptures(), loadedSolution.getRateForAllRups().length);

    }

    public void testSolutionReportPageGen(File solutionFile) throws IOException {
        File outputDir = new File(solutionFile.getParentFile(), "solutionReport");
        outputDir.mkdir();

        NZSHM22_PythonGateway.getReportPageGen()
                .setSolution(solutionFile.getAbsolutePath())
                .setOutputPath(outputDir.getAbsolutePath())
                .addPlot("ParticipationRatePlot")
                .setFillSurfaces(true)
                .generatePage();

        assertTrue(new File(outputDir, "resources/sol_partic_m7.png").exists());
    }

    public void testRupSetReportPageGen(File rupSetFile) throws IOException {
        File outputDir = new File(rupSetFile.getParentFile(), "ruptureReport");
        outputDir.mkdir();

        NZSHM22_PythonGateway.getReportPageGen()
                .setRuptureSet(rupSetFile.getAbsolutePath())
                .setOutputPath(outputDir.getAbsolutePath())
                .addPlot("FaultSectionConnectionsPlot")
                .setFillSurfaces(true)
                .generateRupSetPage();

        assertTrue(new File(outputDir, "resources/sect_connectivity.png").exists());
    }

    public void testHazard(File solutionFile, String backgroundOption) throws DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = NZSHM22_PythonGateway.getHazardCalculatorBuilder();
        builder.setSolutionFile(solutionFile.getAbsolutePath())
                .setLinear(true)
                .setGMPE("BSSA_2014")
                .setForecastTimespan(50)
                .setIntensityMeasurePeriod(10)
                .setBackgroundOption(backgroundOption);

        NZSHM22_HazardCalculator calculator = builder.build();

        System.out.println(calculator.calc(-41.288889, 174.777222));
    }

}
