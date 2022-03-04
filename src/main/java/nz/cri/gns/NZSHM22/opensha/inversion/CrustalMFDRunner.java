package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.reports.NZSHM22_MFDPlot;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CrustalMFDRunner {

    private double totalRateM5_Sans = 3.6;
    private double totalRateM5_TVZ = 0.4;
    private double bValue_Sans = 1.05;
    private double bValue_TVZ = 1.25;
    double minMagSans = 7.0;
    double minMagTvz = 7.0;

    protected NZSHM22_ScalingRelationshipNode scalingRelationship;
    protected NZSHM22_DeformationModel deformationModel = null;
    private NZSHM22_SpatialSeisPDF spatialSeisPDF = null;
    protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;

    String outputPath = "TEST/mfd";
    String name = "MFD Plot";
    private double mfdUncertaintyPower;
    private double mfdUncertaintyScalar;
    
	public CrustalMFDRunner setScalingRelationship(String scalingRelationship, boolean recalcMags) {
        return setScalingRelationship(NZSHM22_ScalingRelationshipNode.createRelationShip(scalingRelationship), recalcMags);
    }

    public CrustalMFDRunner setScalingRelationship(RupSetScalingRelationship scalingRelationship, boolean recalcMags) {
        this.scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        this.scalingRelationship.setScalingRelationship(scalingRelationship);
        this.scalingRelationship.setRecalc(recalcMags);
        return this;
    }

    public CrustalMFDRunner setDeformationModel(String modelName) {
        Preconditions.checkArgument(rupSet == null, "rupture set must be set after deformation model.");
        this.deformationModel = NZSHM22_DeformationModel.valueOf(modelName);
        return this;
    }

    public CrustalMFDRunner setSpatialSeisPDF(NZSHM22_SpatialSeisPDF spatialSeisPDF) {
        this.spatialSeisPDF = spatialSeisPDF;
        return this;
    }

    public CrustalMFDRunner setMinMags(double minMagSans, double minMagTvz){
        this.minMagSans = minMagSans;
        this.minMagTvz = minMagTvz;
        return this;
    }

    public CrustalMFDRunner setMfdUncertaintyPower(double mfdUncertaintyPower){
        this.mfdUncertaintyPower = mfdUncertaintyPower;
        return this;
    }

    public CrustalMFDRunner setMfdUncertaintyScalar(double mfdUncertaintyScalar) {
		this.mfdUncertaintyScalar = mfdUncertaintyScalar;
		return this;
	}
    
    public CrustalMFDRunner setName(String name) {
        this.name = name;
        return this;
    }

    public CrustalMFDRunner setGutenbergRichterMFD(double totalRateM5_Sans, double totalRateM5_TVZ,
                                                   double bValue_Sans, double bValue_TVZ) {
        this.totalRateM5_Sans = totalRateM5_Sans;
        this.totalRateM5_TVZ = totalRateM5_TVZ;
        this.bValue_Sans = bValue_Sans;
        this.bValue_TVZ = bValue_TVZ;
        return this;
    }

    protected void setupLTB(NZSHM22_LogicTreeBranch branch) {
        if (scalingRelationship != null) {
            branch.clearValue(NZSHM22_ScalingRelationshipNode.class);
            branch.setValue(scalingRelationship);
        }
        if (deformationModel != null) {
            branch.setValue(deformationModel);
        }
        if (spatialSeisPDF != null) {
            branch.clearValue(NZSHM22_SpatialSeisPDF.class);
            branch.setValue(spatialSeisPDF);
        }
    }

    public CrustalMFDRunner setRuptureSetFile(File ruptureSetFile) throws DocumentException, IOException {
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        setupLTB(branch);
        this.rupSet = NZSHM22_InversionFaultSystemRuptSet.loadRuptureSet(ruptureSetFile, branch);

        return this;
    }

    public CrustalMFDRunner setOutputPath(String path) {
        this.outputPath = path;
        return this;
    }

    public void run() throws IOException {
        NZSHM22_CrustalInversionTargetMFDs targetMfds = new NZSHM22_CrustalInversionTargetMFDs(rupSet, totalRateM5_Sans, totalRateM5_TVZ, bValue_Sans, bValue_TVZ, minMagSans, minMagTvz, 15, 15, mfdUncertaintyPower, 0.606);
        rupSet.addModule(targetMfds);

        //rupSet.write(new File(outputPath, "rupSet.zip"));

        NZSHM22_MFDPlot plot = new NZSHM22_MFDPlot();
        RupSetMetadata rupMeta = new RupSetMetadata(name, rupSet);
        ReportMetadata meta = new ReportMetadata(rupMeta);

        ReportPageGen rupReport = new ReportPageGen(meta, new File(outputPath), List.of(plot));
        rupReport.generatePage();
    }

    public static void main(String[] args) throws DocumentException, IOException {

        CrustalMFDRunner runner = new CrustalMFDRunner()
                .setName("NZSHM22 MFD Plots")
                .setOutputPath("TEST/mfd")
                .setScalingRelationship("SMPL_NZ_INT_UP", false)
                .setRuptureSetFile(new File("C:\\Users\\volkertj\\Downloads\\RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0)_bi(F)_stGrSp(2)_coFr(0.5)(4).zip"))
                .setGutenbergRichterMFD(4.3, 0.8, 0.89, 0.89)
                .setMinMags(6.95, 6.95);

        runner.run();
    }


}
