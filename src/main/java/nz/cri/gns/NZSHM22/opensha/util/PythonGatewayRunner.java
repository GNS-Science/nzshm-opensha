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

        File inputDir = new File("/tmp");
        File outputRoot = new File("/tmp");
        File ruptureSet = new File(
            "/home/chrisdc/NSHM/DEV/rupture_sets/NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw==.zip");
//        		"./src/test/resources/RupSet_Az_FM(CFM_0_9_SANSTVZ_D90)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(100)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.2).zip" );
//                "C:\\Code\\NZSHM\\nzshm-opensha\\src\\test\\resources\\AlpineVernonInversionSolution.zip");
//				"RupSet_Cl_FM(CFM_0_9_SANSTVZ_2010)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2).zip");
//        		"C:\\Users\\volkertj\\Downloads\\RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0)_bi(F)_stGrSp(2)_coFr(0.5)(5).zip");
        File outputDir = new File(outputRoot, "inversions");
        Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship)NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        NZSHM22_PythonGateway.CachedCrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();

        ((NZSHM22_PythonGateway.CachedCrustalInversionRunner)runner
                .setSlipRateFactor(0.9, 0.5)
                .setMaxMags("FILTER_RUPSET",10,7.5)
                .setMinMags(6.8 , 6.5)
                .setMaxMags("MANIPULATE_MFD", 10,7.5)
            //  .setInitialSolution("C:\\tmp\\rates.csv")
                .setInversionSeconds(1)
                .setScalingRelationship(scaling, true)
            //   .setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(ruptureSet)
            // .setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setUncertaintyWeightedMFDWeights(10000, .75, 0.4)
            //    .setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setReweightTargetQuantity("MAD"))
                .setGutenbergRichterMFD(3.9, 1.0, 0.9, 1.2, 7.85)
                .setPaleoRateConstraints(0.01, 1000, "GEODETIC_SLIP_1_0", "UCERF3_PLUS_PT25");


        
        // see org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params
		//    	/**
		//    	 * classical SA cooling schedule (Geman and Geman, 1984) (slow but ensures convergence)
		//    	 */
		//    	CLASSICAL_SA,
		//    	/**
		//    	 * fast SA cooling schedule (Szu and Hartley, 1987)
		//    	 */
		//    	FAST_SA,
		//    	/**
		//    	 * very fast SA cooling schedule (Ingber, 1989) (recommended)
		//    	 */
		//    	VERYFAST_SA,
		//    	LINEAR; // Drops temperature uniformly from 1 to 0.  Only use with a completion criteria of a fixed number of iterations.
        runner
        	.setCoolingSchedule("FAST_SA")
        	.setIterationCompletionCriteria(18000000)
        	.setPerturbationFunction("POWER_LAW");

        runner.runInversion();
        runner.writeSolution(new File(outputDir, "crustalInversion.zip").getAbsolutePath());
        

    }
}
