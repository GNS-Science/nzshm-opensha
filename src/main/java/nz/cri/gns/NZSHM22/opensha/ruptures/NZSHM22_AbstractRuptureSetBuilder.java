package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public abstract class NZSHM22_AbstractRuptureSetBuilder {
	
	PlausibilityConfiguration plausibilityConfig;
	
    protected FaultSectionList subSections;
    List<ClusterRupture> ruptures;
    ClusterRuptureBuilder builder;

    File fsdFile = null;
    File downDipFile = null;
    String downDipFaultName = null;
    NZSHM22_FaultModels faultModel = null;

    int minSubSectsPerParent = 2; // 2 are required for UCERf3 azimuth calcs
    int minSubSections = 2; // New NZSHM22

    long maxFaultSections = 100000; // maximum fault ruptures to process
    long skipFaultSections = 0; // skip n fault ruptures, default 0"
    double maxSubSectionLength = 0.5; // maximum sub section length (in units of DDW)
    int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors

	protected RupSetScalingRelationship scalingRelationship = ScalingRelationships.SHAW_2009_MOD;
	protected SlipAlongRuptureModels slipAlongRuptureModel = SlipAlongRuptureModels.UNIFORM;

    protected boolean invertRake = false;
    String scaleDepthIncludeDomainNo;
    double scaleDepthIncludeDomainScalar;
    String scaleDepthExcludeDomainNo;
    double scaleDepthExcludeDomainScalar;


    /**
     * For debugging only. adds 180 degrees to each rake in the fault model
     * @param invertRake
     * @return
     */
    public NZSHM22_AbstractRuptureSetBuilder setInvertRake(boolean invertRake){
        this.invertRake = invertRake;
        return this;
    }

    public NZSHM22_AbstractRuptureSetBuilder setScaleDepthIncludeDomain(String domainNo, double scalar){
        this.scaleDepthIncludeDomainNo = domainNo;
        this.scaleDepthIncludeDomainScalar = scalar;
        return this;
    }

    public NZSHM22_AbstractRuptureSetBuilder setScaleDepthExcludeDomain(String domainNo, double scalar){
        this.scaleDepthExcludeDomainNo = domainNo;
        this.scaleDepthExcludeDomainScalar = scalar;
        return this;
    }

    protected static String fmt(float d) {
        if (d == (long) d)
            return String.format("%d", (long) d);
        else
            return Float.toString(d);
    }

    protected static String fmt(double d) {
        if (d == (long) d)
            return String.format("%d", (long) d);
        else
            return Double.toString(d);
    }

    protected static String fmt(boolean b) {
        return b ? "T" : "F";
    }
    
    public String getDescriptiveName() {
        String description = "";
        if (faultModel != null) {
            description = description + "_FM(" + faultModel.name() + ")";
        }
        if (fsdFile != null) {
            description = description + "_FF(" + fsdFile.getName() + ")";
        }
        if (downDipFile != null) {
            description = description + "_SF(" + downDipFile.getName() + ")";
        }        
        description += "_mnSbS(" + fmt(minSubSections) + ")";
        description += "_mnSSPP(" + fmt(minSubSectsPerParent) + ")";
        description += "_mxSSL(" + fmt(maxSubSectionLength) + ")";
        
        if (maxFaultSections != 100000) {
        	description += "_mxFS(" + fmt(maxFaultSections) + ")";
        }
        if (skipFaultSections != 0) {
        	description += "_skFS(" + fmt(skipFaultSections) + ")";       	
        }
        return description;
    }
    
    public abstract FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException ;

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

    
	public NZSHM22_AbstractRuptureSetBuilder setScalingRelationship(RupSetScalingRelationship scalingRelationship) {
		this.scalingRelationship = scalingRelationship;
		return this;
	}
	
	public RupSetScalingRelationship getScalingRelationship() {
		return this.scalingRelationship;
	}
	
	public NZSHM22_AbstractRuptureSetBuilder setSlipAlongRuptureModel(SlipAlongRuptureModels slipAlongRuptureModel) {
		this.slipAlongRuptureModel = slipAlongRuptureModel;
		return this;
	}
	
	public SlipAlongRuptureModels getSlipAlongRuptureModel() {
		return this.slipAlongRuptureModel;
	}	
    
//    /**
//     * Sets the subduction fault. At the moment, only one fault can be set.
//     *
//     * @param faultName   The name fo the fault.
//     * @param downDipFile the CSV file containing all sections.
//     * @return this builder
//     */
//    public NZSHM22_AbstractRuptureSetBuilder setSubductionFault(String faultName, File downDipFile) {
//        this.downDipFaultName = faultName;
//        this.downDipFile = downDipFile;
//        return this;
//    }


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

    /**
     * @param minSubSections sets the minimum subsections, 2 is default
     * @return NZSHM22_RuptureSetBuilder the builder
     */
    public NZSHM22_AbstractRuptureSetBuilder setMinSubSections(int minSubSections) {
        this.minSubSections = minSubSections;
        return this;
    }

    protected void applyDepthScalars(FaultSectionList sections) {
        if (scaleDepthIncludeDomainNo != null) {
            Preconditions.checkState(((NZFaultSection) sections.get(0)).getDomainNo() != null,
                    "fault model must have domain data when using scaleDepthIncludeDomain");
            for (FaultSection section : sections) {
                if (((NZFaultSection) section).getDomainNo().equals(scaleDepthIncludeDomainNo)) {
                    ((FaultSectionPrefData) section).setAveLowerDepth(section.getAveLowerDepth() * scaleDepthIncludeDomainScalar);
                }
            }
        }
        if (scaleDepthExcludeDomainNo != null) {
            Preconditions.checkState(((NZFaultSection) sections.get(0)).getDomainNo() != null,
                    "fault model must have domain data when using scaleDepthExcludeDomain");
            for (FaultSection section : sections) {
                if (!((NZFaultSection) section).getDomainNo().equals(scaleDepthExcludeDomainNo)) {
                    ((FaultSectionPrefData) section).setAveLowerDepth(section.getAveLowerDepth() * scaleDepthExcludeDomainScalar);
                }
            }
        }
    }

    protected void loadFaults(NZSHM22_FaultModels faultModel) throws DocumentException, IOException {
        faultModel.fetchFaultSections(subSections);

        System.out.println("Fault model has " + subSections.size() + " fault sections");

        if (invertRake) {
            for (FaultSection section : subSections) {
                double rake = section.getAveRake() + 180;
                if (rake >= 360) {
                    rake -= 180;
                }
                section.setAveRake(rake);
            }
        }

        applyDepthScalars(subSections);

        subSections.removeIf(faultSection -> faultSection.getFaultTrace().get(0).getLongitude() > 170 ||
                faultSection.getFaultTrace().get(faultSection.getFaultTrace().size() - 1).getLongitude() > 170);
        subSections.removeIf(faultSection -> !(faultSection instanceof DownDipFaultSection) && !faultSection.getSectionName().startsWith("Alpine"));

        if (faultModel.isCrustal()) {

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

    protected void loadFaults() throws IOException, DocumentException {
        if (faultModel != null) {
            faultModel.fetchFaultSections(subSections);
        } else if (downDipFile != null) {
            try (FileInputStream in = new FileInputStream(downDipFile)) {
                DownDipSubSectBuilder.loadFromStream(subSections, 10000, downDipFaultName, in);
            }
        } else if (fsdFile != null) {
            Document doc = XMLUtils.loadDocument(fsdFile);
            NZSHM22_FaultModels.loadStoredFaultSections(subSections, doc);
        } else {
            throw new IllegalArgumentException("No fault model specified.");
        }

        System.out.println("Fault model has " + subSections.size() + " fault sections");

        if (invertRake) {
            for (FaultSection section : subSections) {
                double rake = section.getAveRake() + 180;
                if (rake >= 360) {
                    rake -= 180;
                }
                section.setAveRake(rake);
            }
        }

        applyDepthScalars(subSections);

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

    public NZSHM22_LogicTreeBranch getLogicTreeBranch(FaultRegime regime){
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(regime);
        if(faultModel != null) {
            branch.setValue(faultModel);
        }
        branch.setValue(new NZSHM22_ScalingRelationshipNode(scalingRelationship));
        branch.setValue(slipAlongRuptureModel);
        return branch;
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
    
	/**
	 * @return the plausabilityConfig
	 */
	public PlausibilityConfiguration getPlausibilityConfig() {
		return plausibilityConfig;
	}    
}
