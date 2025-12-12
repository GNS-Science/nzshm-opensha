package nz.cri.gns.NZSHM22.opensha.inversion.constraint.joint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintRegionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintWrapper;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;

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
        ConstraintRegionConfig cruConfig = new ConstraintRegionConfig(List.of(CRU_SECTION));
        ConstraintRegionConfig subConfig = new ConstraintRegionConfig(List.of(SUB_SECTION));

        SlipRateInversionConstraint slipConstraint =
                new SlipRateInversionConstraint(1, ConstraintWeightingType.UNNORMALIZED, rupSet);

        DoubleMatrix2D A =
                new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        double[] d = new double[slipConstraint.getNumRows()];

        slipConstraint.encode(A, d, 0);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

        ConstraintRegionConfig config =
                new ConstraintRegionConfig(List.of(CRU_SECTION, SUB_SECTION));
        JointConstraintWrapper wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);

        A = new DenseDoubleMatrix2D(wrappedConstraint.getNumRows(), rupSet.getNumRuptures());
        d = new double[wrappedConstraint.getNumRows()];

        wrappedConstraint.encode(A, d, 0);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

         config =
                new ConstraintRegionConfig(List.of(CRU_SECTION));
         wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);

        A = new DenseDoubleMatrix2D(wrappedConstraint.getNumRows(), rupSet.getNumRuptures());
        d = new double[wrappedConstraint.getNumRows()];

        wrappedConstraint.encode(A, d, 0);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

        config =
                new ConstraintRegionConfig(List.of(SUB_SECTION));
        wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);

        A = new DenseDoubleMatrix2D(wrappedConstraint.getNumRows(), rupSet.getNumRuptures());
        d = new double[wrappedConstraint.getNumRows()];

        wrappedConstraint.encode(A, d, 0);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

        A = new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        d = new double[2];

        config =
                new ConstraintRegionConfig(List.of(CRU_SECTION));
        wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);
        wrappedConstraint.encode(A, d, 0);

        config =
                new ConstraintRegionConfig(List.of(SUB_SECTION));
        wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);
        wrappedConstraint.encode(A, d, 1);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

        A = new DenseDoubleMatrix2D(2, rupSet.getNumRuptures());
        d = new double[2];

        config =
                new ConstraintRegionConfig(List.of(CRU_SECTION));
        wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);
        wrappedConstraint.encode(A, d, 0);

        config =
                new ConstraintRegionConfig(List.of(SUB_SECTION));
         slipConstraint =
                new SlipRateInversionConstraint(0.5, ConstraintWeightingType.UNNORMALIZED, rupSet);
        wrappedConstraint =
                new JointConstraintWrapper(config, slipConstraint);
        wrappedConstraint.encode(A, d, 1);

        System.out.println(A);
        System.out.println(Arrays.toString(d));

        // FIXME: check encode return value

    }
}
