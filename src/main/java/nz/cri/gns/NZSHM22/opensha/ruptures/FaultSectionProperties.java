package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/** A module to side-load additional fault section properties */
public class FaultSectionProperties implements FileBackedModule {

    protected List<Map<String, Object>> data = new ArrayList<>();

    public FaultSectionProperties() {}

    /**
     * Set a property on a fault section. It is expected but not policed that all values of a
     * property have the same type.
     *
     * @param sectionId the section id
     * @param property the property name
     * @param value the value
     */
    public void set(int sectionId, String property, Object value) {
        while (data.size() <= sectionId) {
            data.add(null);
        }
        Map<String, Object> properties = data.get(sectionId);
        if (properties == null) {
            properties = new LinkedHashMap<>();
            data.set(sectionId, properties);
        }
        properties.put(property, value);
    }

    /**
     * Gets all properties for a specific fault section.
     *
     * @param sectionId the fault section id
     * @return a Map of properties, or null if no properties are set on the section
     */
    public Map<String, Object> get(int sectionId) {
        if (data.size() < sectionId + 1) {
            return null;
        }
        return data.get(sectionId);
    }

    /**
     * Gets a property for a specific fault section.
     *
     * @param sectionId the section id
     * @param property the property name
     * @return the value or null if the property has not been set on the section
     */
    public Object get(int sectionId, String property) {
        Map<String, Object> properties = get(sectionId);
        if (properties != null) {
            return properties.get(property);
        }
        return null;
    }

    public Integer getInt(int sectionId, String property) {
        Object value = get(sectionId, property);
        if (value == null) {
            return null;
        }
        // Should only be an Integer if the data does not come from json. For example, in tests.
        if (value instanceof Integer) {
            return (Integer) value;
        }
        double dValue = (Double) value;
        Preconditions.checkState(Math.rint(dValue) == dValue);
        return (int) dValue;
    }

    @Override
    public String getFileName() {
        return "NZSHM_FaultSectionProperties.json";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(data);
        out.write(json.getBytes());
        out.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String json = new String(bytes, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        data = gson.fromJson(json, List.class);
    }

    @Override
    public String getName() {
        return "FaultSectionProperties";
    }

    public static void backfill() throws IOException, DocumentException {
        // Backfill module for existing rupture set
        // This should work for all crustal, subduction, and joint rupture sets.
        // Ensure to use the correct fault model if there are crustal sections.
        // Crustal sections must come before subduction sections so that section ids line up.

        String ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\mergedRupset_5km_cffPatch2km_cff0SelfStiffness.zip";
        ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
        ruptureSetName =
                "C:\\Users\\volkertj\\Code\\ruptureSets\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip";

        FaultSectionProperties properties = new FaultSectionProperties();
        FaultSystemRupSet ruptureSet = FaultSystemRupSet.load(new File(ruptureSetName));

        // faultmodel is only used for crustal sections
        NZSHM22_FaultModels faultModel = NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ;
        FaultSectionList parentSections = new FaultSectionList();
        faultModel.fetchFaultSections(parentSections);

        int hikurangiCount = 0;
        int puysegurCount = 0;

        for (FaultSection section : ruptureSet.getFaultSectionDataList()) {
            if (section.getSectionName().contains("row:")) {
                //  Backfill subduction props
                properties.set(section.getSectionId(), "origParent", 10000);
                if (section.getSectionName().contains("Hikurangi")) {
                    properties.set(
                            section.getSectionId(), PartitionPredicate.HIKURANGI.name(), true);
                    properties.set(section.getSectionId(), "origId", hikurangiCount);
                    hikurangiCount++;
                }
                if (section.getSectionName().contains("Puysegur")) {
                    properties.set(
                            section.getSectionId(), PartitionPredicate.PUYSEGUR.name(), true);
                    properties.set(section.getSectionId(), "origId", puysegurCount);
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

                properties.set(section.getSectionId(), PartitionPredicate.CRUSTAL.name(), true);
                if (faultModel.getTvzDomain() != null
                        && faultModel.getTvzDomain().equals(parent.getDomainNo())) {
                    properties.set(section.getSectionId(), PartitionPredicate.TVZ.name(), true);
                } else {
                    properties.set(
                            section.getSectionId(), PartitionPredicate.SANS_TVZ.name(), true);
                }
            }
        }

        ruptureSet.addModule(properties);

        ruptureSet.write(new File(ruptureSetName + "props2.zip"));
    }

    public static void main(String[] args) throws IOException, DocumentException {
        backfill();
    }
}
