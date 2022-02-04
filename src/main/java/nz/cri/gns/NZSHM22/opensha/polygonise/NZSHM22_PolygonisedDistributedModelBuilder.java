package nz.cri.gns.NZSHM22.opensha.polygonise;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import java.io.File;
import java.io.IOException;

public class NZSHM22_PolygonisedDistributedModelBuilder {

    protected File solutionFile;
    protected double rateWeight;
    protected double pdfWeight;
    protected FaultSystemSolution solution;

    public NZSHM22_PolygonisedDistributedModelBuilder() {
    }

    protected void scaleRuptureRates(FaultSystemSolution solution, double weight) {
        double[] rates = solution.getRateForAllRups();
        for (int i = 0; i < rates.length; i++) {
            rates[i] *= weight;
        }
    }

    protected void scalePDF(FaultSystemSolution solution, double weight) {
        NZSHM22_LogicTreeBranch branch = solution.getRupSet().getModule(NZSHM22_LogicTreeBranch.class);
        NZSHM22_SpatialSeisPDF spatialSeisPDF = branch.getValue(NZSHM22_SpatialSeisPDF.class);
        FaultSectionPolygonWeights weights = new FaultSectionPolygonWeights(solution);

        NZSHM22_GriddedData griddedData = spatialSeisPDF.getGriddedData().transform((location, value) -> value * weight * weights.getWeight(location));

        solution.addModule(new NZSHM22_PolygonisedDistributedModel(griddedData));
        branch.clearValue(NZSHM22_SpatialSeisPDF.class);
        branch.setValue(NZSHM22_SpatialSeisPDF.FROM_SOLUTION);
    }

    public NZSHM22_PolygonisedDistributedModelBuilder setSolution(String fileName) {
        solutionFile = new File(fileName);
        return this;
    }

    public NZSHM22_PolygonisedDistributedModelBuilder setWeights(double rateWeight, double pdfWeight) {
        this.rateWeight = rateWeight;
        this.pdfWeight = pdfWeight;
        return this;
    }

    public NZSHM22_PolygonisedDistributedModelBuilder build() throws IOException {
        solution = NZSHM22_InversionFaultSystemSolution.fromFile(solutionFile);
        scaleRuptureRates(solution, rateWeight);
        scalePDF(solution, pdfWeight);
        return this;
    }

    public void save(String fileName) throws IOException {
        solution.write(new File(fileName));
    }

    public static void main(String[] args) throws IOException {
        new NZSHM22_PolygonisedDistributedModelBuilder()
                .setSolution("C:\\Code\\NZSHM\\nzshm-opensha\\TEST\\inversions\\CrustalInversionSolution.zip")
                .setWeights(0.8, 0.2)
                .build()
                .save("C:\\Code\\NZSHM\\nzshm-opensha\\TEST\\inversions\\PolygonisedCrustalInversionSolution.zip");
    }

}
