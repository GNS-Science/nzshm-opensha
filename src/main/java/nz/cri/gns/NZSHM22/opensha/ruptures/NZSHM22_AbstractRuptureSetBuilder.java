package nz.cri.gns.NZSHM22.opensha.ruptures;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public abstract class NZSHM22_AbstractRuptureSetBuilder {
    FaultSectionList subSections;
    List<ClusterRupture> ruptures;
    ClusterRuptureBuilder builder;

    File fsdFile = null;
    File downDipFile = null;
    String downDipFaultName = null;
    Set<Integer> faultIds;
    NZSHM22_FaultModels faultModel = null;

    int minSubSectsPerParent = 2; // 2 are required for UCERf3 azimuth calcs

    long maxFaultSections = 100000; // maximum fault ruptures to process
    long skipFaultSections = 0; // skip n fault ruptures, default 0"
    double maxSubSectionLength = 0.5; // maximum sub section length (in units of DDW)
    int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors

    public abstract String getDescriptiveName();

    public abstract NZSHM22_SlipEnabledRuptureSet buildRuptureSet() throws DocumentException, IOException ;

    public NZSHM22_AbstractRuptureSetBuilder setFaultModel(NZSHM22_FaultModels faultModel){
        this.faultModel = faultModel;
        return this;
    }

    /**
     * Sets the FaultModel file for all crustal faults
     *
     * @param fsdFile the XML FaultSection data file containing source fault
     *                information
     * @return this builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setFaultModelFile(File fsdFile) {
        this.fsdFile = fsdFile;
        return this;
    }

    /**
     * Sets the subduction fault. At the moment, only one fault can be set.
     *
     * @param faultName   The name fo the fault.
     * @param downDipFile the CSV file containing all sections.
     * @return this builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setSubductionFault(String faultName, File downDipFile) {
        this.downDipFaultName = faultName;
        this.downDipFile = downDipFile;
        return this;
    }


    /**
     * Used for testing only!
     *
     * @param maxFaultSections the maximum number of fault sections to be processed.
     * @return NZSHM22_RuptureSetBuilder the builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setMaxFaultSections(int maxFaultSections) {
        this.maxFaultSections = maxFaultSections;
        return this;
    }

    /**
     * Used for testing only!
     *
     * @param skipFaultSections sets the number fault sections to be skipped.
     * @return NZSHM22_RuptureSetBuilder the builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setSkipFaultSections(int skipFaultSections) {
        this.skipFaultSections = skipFaultSections;
        return this;
    }

    /**
     * Some internal classes support parallelisation.
     *
     * @param numThreads sets munber of threads to be used.
     * @return NZSHM22_RuptureSetBuilder the builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setNumThreads(int numThreads) {
        this.numThreads = numThreads;
        return this;
    }

    /**
     * @param minSubSectsPerParent sets the minimum subsections per parent, 2 is
     *                             standard as per UCERF3
     * @return NZSHM22_RuptureSetBuilder the builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setMinSubSectsPerParent(int minSubSectsPerParent) {
        this.minSubSectsPerParent = minSubSectsPerParent;
        return this;
    }

    protected void loadFaults() throws IOException, DocumentException {
        if (faultModel != null) {
            faultModel.fetchFaultSections(subSections);
        } else if (downDipFile != null) {
            try (FileInputStream in = new FileInputStream(downDipFile)) {
                DownDipSubSectBuilder.loadFromStream(subSections, 10000, downDipFaultName, in);
            }
        } else if (fsdFile != null) {
            subSections = FaultSectionList.fromList((FaultModels.loadStoredFaultSections(fsdFile)));
        } else {
            throw new IllegalArgumentException("No fault model specified.");
        }

        System.out.println("Fault model has " + subSections.size() + " fault sections");

        if (fsdFile != null || (faultModel != null && faultModel.isCrustal())) {

            if (maxFaultSections < 1000 || skipFaultSections > 0) {
                final long endSection = maxFaultSections + skipFaultSections;
                final long skipSections = skipFaultSections;
                subSections.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
                System.out.println("Fault model filtered to " + subSections.size() + " fault sections");
            }

            FaultSectionList fsd = subSections;
            subSections = new FaultSectionList();
            // build the subsections
            subSections.addParents(fsd);
            for (FaultSection parentSect : fsd) {
                double ddw = parentSect.getOrigDownDipWidth();
                double maxSectLength = ddw * maxSubSectionLength;
                System.out.println("Get subSections in " + parentSect.getName());
                // the "2" here sets a minimum number of sub sections
                List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength,
                        subSections.getSafeId(), 2);
                getSubSections().addAll(newSubSects);
                System.out.println("Produced " + newSubSects.size() + " subSections in " + parentSect.getName());
            }
            System.out.println(subSections.size() + " Sub Sections created.");
        }
    }

    /**
     * @return the ruptures
     */
    public List<ClusterRupture> getRuptures() {
        return ruptures;
    }

    /**
     * @return the subSections
     */
    public FaultSectionList getSubSections() {
        return subSections;
    }

    /**
     * @return the builder
     */
    public ClusterRuptureBuilder getBuilder() {
        return builder;
    }
}
