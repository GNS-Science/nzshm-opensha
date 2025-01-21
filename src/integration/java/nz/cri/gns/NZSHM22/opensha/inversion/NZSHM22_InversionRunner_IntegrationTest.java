package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_InversionRunner_IntegrationTest {

    public ArchiveInput ruptureSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(List.of(0, 1), List.of(5, 6, 7, 8, 9)));
        return TestHelpers.archiveInput(rupSet);
    }

    public NZSHM22_AbstractInversionRunner     buildRunner() throws DocumentException, IOException {
        return new NZSHM22_CrustalInversionRunner()
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85)
                .setInversionSeconds(1)
                .setSelectionInterval(1)
                .setScalingRelationship(ScalingRelationships.SHAW_2009_MOD, false)
                .setRuptureSetArchiveInput(ruptureSet())
                // .setGutenbergRichterMFDWeights(100.0, 1000.0)
                // .setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setUncertaintyWeightedMFDWeights(0.5, 0.5, 0.5);
    }

    @Test
    public void testRunExclusion() throws DocumentException, IOException {
        NZSHM22_AbstractInversionRunner runner = buildRunner().setExcludeRupturesBelowMinMag(true);
        FaultSystemSolution solution = runner.runInversion();

        for (int r = 0; r < solution.getRupSet().getNumRuptures(); r++) {
            if (((NZSHM22_InversionFaultSystemRuptSet) solution.getRupSet())
                    .isRuptureBelowSectMinMag(r)) {
                assertEquals(0, solution.getRateForRup(r), 0);
            }
        }
    }

    /**
     * Test showing how we create a new NZSHM22_InversionFaultSystemRuptSet from an existing rupture
     * set
     *
     * @throws IOException
     * @throws DocumentException
     * @throws URISyntaxException
     */
    @Test
    public void testLoadRuptureSetForInversion() throws IOException, DocumentException {
        NZSHM22_InversionFaultSystemRuptSet ruptureSet =
                NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(
                        ruptureSet(), NZSHM22_LogicTreeBranch.crustalInversion());
        assertEquals(2, ruptureSet.getModule(ClusterRuptures.class).getAll().size());
    }

    // TODO we should use junit>=4.13 and assertThrows instead
    // see
    // https://stackoverflow.com/questions/156503/how-do-you-assert-that-a-certain-exception-is-thrown-in-junit-4-tests
    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner =
                (NZSHM22_CrustalInversionRunner)
                        new NZSHM22_CrustalInversionRunner()
                                .setSlipRateConstraint(
                                        AbstractInversionConfiguration
                                                .NZSlipRateConstraintWeightingType
                                                .NORMALIZED_BY_UNCERTAINTY,
                                        1,
                                        2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument() {
        new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(
                        AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                                .NORMALIZED_BY_UNCERTAINTY,
                        1,
                        2);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument2() {
        new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(
                        AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED,
                        1,
                        2)
                .setSlipRateUncertaintyConstraint(1, 2);
    }
}
