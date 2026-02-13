package nz.cri.gns.NZSHM22.opensha.faults;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class NZFaultSection extends GeoJSONFaultSection {

    public static final String DOMAIN = "Domain";
    public static final String ORIGINAL_PARENT = "OriginalParent";
    public static final String ORIGINAL_ID = "OriginalId";
    public static final String PARTITION = "Partition";

    protected String domainNo;
    protected String domainName;

    public NZFaultSection(FaultSection sect) {
        super(sect);
    }

    public String getDomainNo() {
        return (String) getProperty(DOMAIN);
    }

    public void setOriginalParentId(int parentId) {
        setProperty(ORIGINAL_PARENT, parentId);
    }

    public Integer getOriginalParentId() {
        return getInt(ORIGINAL_PARENT);
    }

    public void setOriginalId(int sectionId) {
        setProperty(ORIGINAL_ID, sectionId);
    }

    public Integer getOriginalId() {
        return getInt(ORIGINAL_ID);
    }

    public void setPartition(PartitionPredicate partition) {
        setProperty(PARTITION, partition.name());
    }

    public PartitionPredicate getPartition() {
        Object value = getProperty(PARTITION);
        if (value instanceof String) {
            return PartitionPredicate.valueOf((String) value);
        }
        return null;
    }

    public void setTvz() {
        setProperty(PartitionPredicate.TVZ.name(), true);
    }

    public boolean isTvz() {
        return getProperty(PartitionPredicate.TVZ.name()) == Boolean.TRUE;
    }

    protected Integer getInt(String name) {
        Object value = getProperty(name);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return (int) value;
        }
        return null;
    }

    public static NZFaultSection fromXMLMetadata(Element el) {
        FaultSectionPrefData data = FaultSectionPrefData.fromXMLMetadata(el);
        NZFaultSection section = new NZFaultSection(data);
        section.getProperties().set(DOMAIN, el.attributeValue("domainNo"));
        return section;
    }

    public static void enhanceFaultSections(List<? extends FaultSection> sections) {
        @SuppressWarnings("unchecked")
        List<FaultSection> ss = (List<FaultSection>) sections;
        for (int s = 0; s < sections.size(); s++) {
            FaultSection section = sections.get(s);
            if (!(section instanceof NZFaultSection)) {
                ss.set(s, new NZFaultSection(section));
            }
        }
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

        enhanceFaultSections(ruptureSet.getFaultSectionDataList());

        for (FaultSection s : ruptureSet.getFaultSectionDataList()) {
            NZFaultSection section = (NZFaultSection) s;
            if (section.getSectionName().contains("row:")) {
                //  Backfill subduction props
                section.setOriginalParentId(10000);
                if (section.getSectionName().contains("Hikurangi")) {
                    section.setPartition(PartitionPredicate.HIKURANGI);
                    section.setOriginalId(hikurangiCount);
                    hikurangiCount++;
                }
                if (section.getSectionName().contains("Puysegur")) {
                    section.setPartition(PartitionPredicate.PUYSEGUR);
                    section.setOriginalId(puysegurCount);
                    puysegurCount++;
                }
            } else {
                // backfill crustal props
                NZFaultSection parent =
                        (NZFaultSection) parentSections.get(section.getParentSectionId());

                Preconditions.checkState(
                        section.getParentSectionName().equals(parent.getSectionName()));

                section.setPartition(PartitionPredicate.CRUSTAL);
                if (faultModel.getTvzDomain() != null
                        && faultModel.getTvzDomain().equals(parent.getDomainNo())) {
                    section.setTvz();
                }
            }
        }

        ruptureSet.write(new File(ruptureSetName + "props3.zip"));
    }

    public static void main(String[] args) throws DocumentException, IOException {
        backfillProperties();
    }
}
