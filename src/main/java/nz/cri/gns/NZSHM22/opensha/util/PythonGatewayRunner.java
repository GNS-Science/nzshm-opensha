package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.IOException;


/**
 * This is for Oakley to test new functionality in the gateway during development.
 */
public class PythonGatewayRunner {

    public static void main(String[] args) throws DocumentException, IOException {

        File inputDir = new File("./TEST");
        File outputRoot = new File("./TEST");
        File ruptureSet = new File(
                "C:\\Code\\NZSHM\\nzshm-opensha\\src\\test\\resources\\AlpineVernonInversionSolution.zip");
//				"RupSet_Cl_FM(CFM_0_9_SANSTVZ_2010)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2).zip");
//        		"C:\\Users\\volkertj\\Downloads\\RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0)_bi(F)_stGrSp(2)_coFr(0.5)(5).zip");
        File outputDir = new File(outputRoot, "inversions");
        Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship)NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        NZSHM22_PythonGateway.CachedCrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();

        ((NZSHM22_PythonGateway.CachedCrustalInversionRunner)runner
                .setInversionSeconds(1)
                .setScalingRelationship(scaling, true)
                //   .setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(ruptureSet.getAbsolutePath())
                .setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setSlipRateConstraint("BOTH", 1000, 1000))
                .setSlipRateUncertaintyConstraint("NORMALIZED_BY_UNCERTAINTY", 1000, 2)
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85);

        runner.runInversion();
        runner.writeSolution(new File(outputDir, "crustalInversion.zip").getAbsolutePath());

    }
}
