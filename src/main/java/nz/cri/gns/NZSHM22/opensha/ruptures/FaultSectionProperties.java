package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class FaultSectionProperties {

    public static final String ORIGINAL_PARENT = "OriginalParent";
    public static final String ORIGINAL_ID = "OriginalId";
    public static final String PARTITION = "Partition";

    public static Object get(FaultSection section, String name) {
        if (!(section instanceof GeoJSONFaultSection)) {
            return null;
        }
        FeatureProperties properties = ((GeoJSONFaultSection) section).getProperties();
        return properties.get(name);
    }

    public static Integer getInt(FaultSection section, String name) {
        Object value = get(section, name);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return (int) value;
        }
        return null;
    }

    public static void setOriginalParentId(GeoJSONFaultSection section, int parentId) {
        section.getProperties().set(ORIGINAL_PARENT, parentId);
    }

    public static Integer getOriginalParentId(FaultSection section) {
        return getInt(section, ORIGINAL_PARENT);
    }

    public static void setOriginalId(GeoJSONFaultSection section, int sectionId) {
        section.getProperties().set(ORIGINAL_ID, sectionId);
    }

    public static Integer getOriginalId(FaultSection section) {
        return getInt(section, ORIGINAL_ID);
    }

    public static void setPartition(GeoJSONFaultSection section, PartitionPredicate partition) {
        section.getProperties().set(PARTITION, partition.name());
    }

    public static PartitionPredicate getPartition(FaultSection section) {
        Object value = get(section, PARTITION);
        if (value instanceof String) {
            return PartitionPredicate.valueOf((String) value);
        }
        return null;
    }

    public static void setTvz(GeoJSONFaultSection section) {
        section.getProperties().set(PartitionPredicate.TVZ.name(), true);
    }

    public static boolean isTvz(FaultSection section) {
        return get(section, PartitionPredicate.TVZ.name()) == Boolean.TRUE;
    }

    public static void backfillProperties() throws IOException, DocumentException {
        // Backfill properties for existing rupture set
        // This should work for all crustal, subduction, and joint rupture sets.
        // Ensure to use the correct fault model if there are crustal sections.
        // Crustal sections must come before subduction sections so that section ids line up.

        String ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\mergedRupset_5km_cffPatch2km_cff0SelfStiffness.zip";
        ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
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

        for (FaultSection s : ruptureSet.getFaultSectionDataList()) {
            GeoJSONFaultSection section = (GeoJSONFaultSection) s;
            if (section.getSectionName().contains("row:")) {
                //  Backfill subduction props
                setOriginalParentId(section, 10000);
                if (section.getSectionName().contains("Hikurangi")) {
                    setPartition(section, PartitionPredicate.HIKURANGI);
                    setOriginalId(section, hikurangiCount);
                    hikurangiCount++;
                }
                if (section.getSectionName().contains("Puysegur")) {
                    setPartition(section, PartitionPredicate.PUYSEGUR);
                    setOriginalId(section, puysegurCount);
                    puysegurCount++;
                }
            } else {
                // backfill crustal props
                NZFaultSection parent =
                        (NZFaultSection) parentSections.get(section.getParentSectionId());

                Preconditions.checkState(
                        section.getParentSectionName().equals(parent.getSectionName()));

                setPartition(section, PartitionPredicate.CRUSTAL);
                if (faultModel.getTvzDomain() != null
                        && faultModel.getTvzDomain().equals(parent.getDomainNo())) {
                    setTvz(section);
                }
            }
        }

        ruptureSet.write(new File(ruptureSetName + "props3.zip"));
    }

    public static void main(String[] args) throws DocumentException, IOException {
        backfillProperties();
    }
}
