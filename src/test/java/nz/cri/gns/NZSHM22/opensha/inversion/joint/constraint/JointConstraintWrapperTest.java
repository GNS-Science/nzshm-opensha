package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintWrapper;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public class JointConstraintWrapperTest {

    static final int CRU_SECTION = 0;
    static final int SUB_SECTION = 1;

    public FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        NZSHM22_ScalingRelationshipNode.createRelationShip("TMG_CRU_2017"),
                        List.of(
                                List.of(CRU_SECTION),
                                List.of(SUB_SECTION),
                                List.of(CRU_SECTION, SUB_SECTION)));

        rupSet.getFaultSectionDataList().removeIf((s) -> s.getSectionId() > 1);

        FaultSectionProperties props = new FaultSectionProperties();
        props.set(CRU_SECTION, PartitionPredicate.CRUSTAL.name(), true);
        props.set(SUB_SECTION, PartitionPredicate.HIKURANGI.name(), true);
        rupSet.addModule(props);

        double[] aveSlipData = new double[rupSet.getNumRuptures()];
        aveSlipData[0] = 1;
        aveSlipData[1] = 2;
        aveSlipData[2] = 3;
        double[] slipRateData = new double[rupSet.getNumSections()];
        slipRateData[0] = 1;
        slipRateData[1] = 2;
        double[] slipStdvData = new double[rupSet.getNumSections()];
        slipStdvData[0] = 1;
        slipStdvData[1] = 2;
        AveSlipModule aveSlip = AveSlipModule.precomputed(rupSet, aveSlipData);
        rupSet.addModule(aveSlip);
        rupSet.addModule(SlipAlongRuptureModels.UNIFORM.getModel());
        SectSlipRates targets = SectSlipRates.precomputed(rupSet, slipRateData, slipStdvData);
        rupSet.addModule(targets);
        return rupSet;
    }

    @Test
    public void encodeTest() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = makeRupSet();
        Config config = new Config();
        config.ruptureSet = rupSet;
        SlipRateInversionConstraint slipConstraint =
                new SlipRateInversionConstraint(1, ConstraintWeightingType.UNNORMALIZED, rupSet);

        // set up crustal constraint
        PartitionConfig cruConfig = new PartitionConfig(PartitionPredicate.CRUSTAL);
        cruConfig.init(config);
        JointConstraintWrapper crustalConstraint =
                new JointConstraintWrapper(cruConfig, slipConstraint);

        // set up subduction constraint
        PartitionConfig subConfig = new PartitionConfig(PartitionPredicate.HIKURANGI);
        subConfig.init(config);
        JointConstraintWrapper subductionConstraint =
                new JointConstraintWrapper(subConfig, slipConstraint);

        // getNumRows()
        assertEquals(rupSet.getNumSections(), slipConstraint.getNumRows());
        assertEquals(1, crustalConstraint.getNumRows());
        assertEquals(1, subductionConstraint.getNumRows());

        // encode
        DoubleMatrix2D originalA = new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        double[] originalD = new double[slipConstraint.getNumRows()];
        long originalResult = slipConstraint.encode(originalA, originalD, 0);
        // We only care about the first two. Shortening for easier comparison.
        originalD = new double[] {originalD[0], originalD[1]};

        DoubleMatrix2D crustalA = new DenseDoubleMatrix2D(1, rupSet.getNumRuptures());
        double[] crustalD = new double[crustalConstraint.getNumRows()];
        long crustalResult = crustalConstraint.encode(crustalA, crustalD, 0);

        DoubleMatrix2D sbdA = new DenseDoubleMatrix2D(1, rupSet.getNumRuptures());
        double[] sbdD = new double[subductionConstraint.getNumRows()];
        long sbdResult = subductionConstraint.encode(sbdA, sbdD, 0);

        assertEquals(originalResult, crustalResult + sbdResult);
        // crustal matrix first row is identical to first row of original matrix
        assertEquals(originalA.viewRow(0), crustalA.viewRow(0));
        // subduction matrix first row is identical to second row of original matrix
        assertEquals(originalA.viewRow(1), sbdA.viewRow(0));

        // The rows of the two wrapped constraints together equal the original matrix.
        // This asserts that startRow is observed.
        DoubleMatrix2D jointA = new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        double[] jointD =
                new double[crustalConstraint.getNumRows() + subductionConstraint.getNumRows()];
        crustalConstraint.encode(jointA, jointD, 0);
        subductionConstraint.encode(jointA, jointD, 1);
        assertEquals(originalA, jointA);
        assertArrayEquals(originalD, jointD, 0.000000000000000001);

        // works without getGetsSets
        slipConstraint.setQuickGetSets(false);
        jointA = new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        jointD = new double[crustalConstraint.getNumRows() + subductionConstraint.getNumRows()];
        crustalConstraint.encode(jointA, jointD, 0);
        subductionConstraint.encode(jointA, jointD, 1);
        assertEquals(originalA, jointA);
        assertArrayEquals(originalD, jointD, 0.000000000000000001);
    }

    @Test
    public void attributesTest() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = makeRupSet();
        Config config = new Config();
        config.ruptureSet = rupSet;
        SlipRateInversionConstraint slipConstraint =
                new SlipRateInversionConstraint(
                        42, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, rupSet);

        PartitionConfig cruConfig = new PartitionConfig(PartitionPredicate.CRUSTAL);
        cruConfig.init(config);
        JointConstraintWrapper crustalConstraint =
                new JointConstraintWrapper(cruConfig, slipConstraint);

        assertEquals(slipConstraint.getWeight(), crustalConstraint.getWeight(), 0.00000001);
        assertEquals(slipConstraint.getName(), crustalConstraint.getName());
        assertEquals(slipConstraint.getShortName(), crustalConstraint.getShortName());
        assertEquals(slipConstraint.getWeightingType(), crustalConstraint.getWeightingType());
        assertEquals(slipConstraint.getName(), crustalConstraint.getName());
    }
}
