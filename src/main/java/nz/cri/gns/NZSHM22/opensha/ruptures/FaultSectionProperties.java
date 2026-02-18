package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/**
 * Access to extra fault section properties that can be stored in the fault section's GeoJson
 * feature properties. Rupture sets in modern, modular OpenSHA archives will have read the fault
 * sections from GeoJson and will use the GeoJSONFaultSection class for fault sections.
 * FaultSectionProperties is a convenience class to get and set common joint inversion properties.
 *
 * <p>Properties modified using this class will be saved in the fault section GeoJson file if the
 * rupture set is written to disk.
 */
public class FaultSectionProperties {

    public static final String PARTITION = "Partition";
    public static final String ORIGINAL_PARENT = "OriginalParent";
    public static final String ORIGINAL_ID = "OriginalId";
    public static final String TVZ = "TVZ";

    final GeoJSONFaultSection section;

    public FaultSectionProperties(FaultSection section) {
        Preconditions.checkArgument(section instanceof GeoJSONFaultSection);
        this.section = (GeoJSONFaultSection) section;
    }

    /**
     * Used when merging two rupture sets that requires parent ids to be re-written.
     *
     * @param originalParent the original parent id
     */
    public void setOriginalParent(int originalParent) {
        section.setProperty(ORIGINAL_PARENT, originalParent);
    }

    public Integer getOriginalParent() {
        return getInt(ORIGINAL_PARENT);
    }

    /**
     * Used when merging two rupture sets that requires section ids to be re-written.
     *
     * @param originalId the original section id
     */
    public void setOriginalId(int originalId) {
        section.setProperty(ORIGINAL_ID, originalId);
    }

    public Integer getOriginalId() {
        return getInt(ORIGINAL_ID);
    }

    public PartitionPredicate getPartition() {

        String partition = (String) get(PARTITION);
        if (partition != null) {
            return PartitionPredicate.valueOf(partition);
        }
        return null;
    }

    /**
     * Set the partition of the section.
     *
     * @param partition the partition
     */
    public void setPartition(PartitionPredicate partition) {
        section.setProperty(PARTITION, partition.name());
    }

    /** Indicates that the section is part of the TVZ. */
    public void setTvz() {
        section.setProperty(TVZ, true);
    }

    public boolean getTvz() {
        return get(TVZ) == Boolean.TRUE;
    }

    public Object get(String property) {
        return section.getProperty(property);
    }

    public Integer getInt(String property) {
        Object value = get(property);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return (int) (long) value;
        }
        double dValue = (Double) value;
        Preconditions.checkState(Math.rint(dValue) == dValue);
        return (int) dValue;
    }

    public static void backfill() throws IOException, DocumentException {
        // Backfill module for existing rupture set
        // This should work for all crustal, subduction, and joint rupture sets.
        // Ensure to use the correct fault model if there are crustal sections.
        // Crustal sections must come before subduction sections so that section ids line up.

        String ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\mergedRupset_5km_cffPatch2km_cff0SelfStiffness.zip";
        //        ruptureSetName =
        //
        // "C:\\Users\\volkertj\\Code\\ruptureSets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
        //        ruptureSetName =
        //
        // "C:\\Users\\volkertj\\Code\\ruptureSets\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip";

        FaultSystemRupSet ruptureSet = FaultSystemRupSet.load(new File(ruptureSetName));

        // faultmodel is only used for crustal sections
        NZSHM22_FaultModels faultModel = NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ;
        FaultSectionList parentSections = new FaultSectionList();
        faultModel.fetchFaultSections(parentSections);

        int hikurangiCount = 0;
        int puysegurCount = 0;

        List<FaultSection> sections = (List<FaultSection>) ruptureSet.getFaultSectionDataList();
        for (int s = 0; s < ruptureSet.getNumSections(); s++) {
            FaultSection original = ruptureSet.getFaultSectionData(s);
            if (!(original instanceof GeoJSONFaultSection)) {
                FaultSection geoSection = new GeoJSONFaultSection(original);
                sections.set(s, geoSection);
            }
        }

        for (FaultSection section : ruptureSet.getFaultSectionDataList()) {
            FaultSectionProperties props = new FaultSectionProperties(section);
            if (section.getSectionName().contains("row:")) {
                //  Backfill subduction props
                props.setOriginalParent(10000);
                if (section.getSectionName().contains("Hikurangi")) {
                    props.setPartition(PartitionPredicate.HIKURANGI);
                    props.setOriginalId(hikurangiCount);
                    hikurangiCount++;
                }
                if (section.getSectionName().contains("Puysegur")) {
                    props.setPartition(PartitionPredicate.PUYSEGUR);
                    props.setOriginalId(puysegurCount);
                    puysegurCount++;
                }
            } else {
                // backfill crustal props
                NZFaultSection parent =
                        (NZFaultSection) parentSections.get(section.getParentSectionId());
                // verify that we're actually using the correct fault model
                //                System.out.println(
                //                        " orig: "
                //                                + section.getParentSectionName()
                //                                + " : model : "
                //                                + parent.getSectionName());
                Preconditions.checkState(
                        section.getParentSectionName().equals(parent.getSectionName()));
                props.setPartition(PartitionPredicate.CRUSTAL);
                if (faultModel.getTvzDomain() != null
                        && faultModel.getTvzDomain().equals(parent.getDomainNo())) {
                    props.setTvz();
                }
            }
        }

        ruptureSet.write(new File(ruptureSetName + "props3.zip"));
    }

    public static void main(String[] args) throws IOException, DocumentException {
        backfill();
    }
}
