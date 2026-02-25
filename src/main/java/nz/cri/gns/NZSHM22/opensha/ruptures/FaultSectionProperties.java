package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/**
 * convenience class to get and set common joint inversion properties. Rupture sets in modern,
 * modular OpenSHA archives will read the fault sections from GeoJson using the GeoJSONFaultSection
 * class. FaultSectionProperties wraps GeoJSONFaultSection.setProperty() and
 * GeoJSONFaultSection.getProperty().
 *
 * <p>Properties modified using this class will be saved in the fault section GeoJson file when the
 * rupture set is written to disk.
 */
public class FaultSectionProperties {

    public static final String PARTITION = "Partition";
    public static final String ORIGINAL_PARENT = "OriginalParent";
    public static final String ORIGINAL_ID = "OriginalId";
    public static final String DOMAIN = "Domain";
    public static final String TVZ = "TVZ";
    public static final String ROW_INDEX = "RowIndex";
    public static final String COL_Index = "ColIndex";
    public static final String DOWNDIP_BUILDER = "DownDipBuilder";

    final GeoJSONFaultSection section;

    /**
     * Creates a new FaultSectionProperties object for the section. The section must be a
     * GeoJSONFaultSection.
     *
     * @param section the fault section to wrap (must be a {@link GeoJSONFaultSection})
     */
    public FaultSectionProperties(FaultSection section) {
        Preconditions.checkArgument(section instanceof GeoJSONFaultSection);
        this.section = (GeoJSONFaultSection) section;
    }

    /**
     * When two rupture sets are merged, this property can be used to preserve the original parent
     * id of the fault section.
     */
    public void setOriginalParent(int originalParent) {
        section.setProperty(ORIGINAL_PARENT, originalParent);
    }

    /**
     * When two rupture sets are merged, this property can be used to preserve the original parent
     * id of the fault section.
     *
     * @return the original parent id or null
     */
    public Integer getOriginalParent() {
        return getInt(ORIGINAL_PARENT);
    }

    /**
     * Convenience static accessor for {@link #getOriginalParent()}.
     *
     * @param section fault section to read from
     * @return original parent id or null if not set
     */
    public static Integer getOriginalParent(FaultSection section) {
        return new FaultSectionProperties(section).getOriginalParent();
    }

    /**
     * When two rupture sets are merged, this property can be used to preserve the original section
     * id.
     *
     * @param originalId the original section id
     */
    public void setOriginalId(int originalId) {
        section.setProperty(ORIGINAL_ID, originalId);
    }

    /**
     * When two rupture sets are merged, this property can be used to preserve the original section
     * id.
     *
     * @return the original fault section id or null
     */
    public Integer getOriginalId() {
        return getInt(ORIGINAL_ID);
    }

    /**
     * Convenience static accessor for {@link #getOriginalId()}.
     *
     * @param section fault section to read from
     * @return original section id or null if not set
     */
    public static Integer getOriginalId(FaultSection section) {
        return new FaultSectionProperties(section).getOriginalId();
    }

    /**
     * The partition of the fault section.
     *
     * @return the partition of the fault section, or null if not set
     */
    public PartitionPredicate getPartition() {

        String partition = (String) section.getProperty(PARTITION);
        if (partition != null) {
            return PartitionPredicate.valueOf(partition);
        }
        return null;
    }

    /**
     * Convenience static accessor for {@link #getPartition()}.
     *
     * @param section fault section to read from
     * @return the section's partition, or null if not set
     */
    public static PartitionPredicate getPartition(FaultSection section) {
        return new FaultSectionProperties(section).getPartition();
    }

    /**
     * Set the partition of the section.
     *
     * @param partition the partition
     */
    public void setPartition(PartitionPredicate partition) {
        section.setProperty(PARTITION, partition.name());
    }

    /**
     * Set the domain for this fault section.
     *
     * @param domain string identifier for the domain (may be null)
     */
    public void setDomain(String domain) {
        section.setProperty(DOMAIN, domain);
    }

    /**
     * Get the domain assigned to this section.
     *
     * @return domain string or null if none set
     */
    public String getDomain() {
        return (String) section.getProperty(DOMAIN);
    }

    /**
     * Convenience static accessor for {@link #getDomain()}.
     *
     * @param section fault section to read from
     * @return domain string or null
     */
    public static String getDomain(FaultSection section) {
        return new FaultSectionProperties(section).getDomain();
    }

    /** Indicates that the section is part of the TVZ. */
    public void setTvz() {
        section.setProperty(TVZ, true);
    }

    /**
     * Indicates whether the section is part of the TVZ
     *
     * @return true if the partition is part of the TVZ
     */
    public boolean getTvz() {
        return section.getProperty(TVZ) == Boolean.TRUE;
    }

    /**
     * Convenience static accessor for {@link #getTvz()}.
     *
     * @param section fault section to check
     * @return true if the section is marked as TVZ
     */
    public static boolean getTvz(FaultSection section) {
        return new FaultSectionProperties(section).getTvz();
    }

    /**
     * Set the row index for the section (used by some grid-based models).
     *
     * @param rowIndex row index to set
     */
    public void setRowIndex(int rowIndex) {
        section.setProperty(ROW_INDEX, rowIndex);
    }

    /**
     * Get the row index if set.
     *
     * @return row index or null if not set
     */
    public Integer getRowIndex() {
        return getInt(ROW_INDEX);
    }

    /**
     * Convenience static accessor for {@link #getRowIndex()}.
     *
     * @param section fault section to read from
     * @return row index or null
     */
    public static Integer getRowIndex(FaultSection section) {
        return new FaultSectionProperties(section).getRowIndex();
    }

    /**
     * Set the column index for the section (used by some grid-based models).
     *
     * @param colIndex column index to set
     */
    public void setColIndex(int colIndex) {
        section.setProperty(COL_Index, colIndex);
    }

    /**
     * Get the column index if set.
     *
     * @return column index or null if not set
     */
    public Integer getColIndex() {
        return getInt(COL_Index);
    }

    /**
     * Convenience static accessor for {@link #getColIndex()}.
     *
     * @param section fault section to read from
     * @return column index or null
     */
    public static Integer getColIndex(FaultSection section) {
        return new FaultSectionProperties(section).getColIndex();
    }

    /**
     * Remove this property before writing the rupture set to file
     *
     * @param builder the builder used to create down-dip subsections
     */
    public void setDownDipBuilder(DownDipSubSectBuilder builder) {
        section.setProperty(DOWNDIP_BUILDER, builder);
    }

    /**
     * Returns the stored down-dip subsection builder for this section, if present.
     *
     * @return the DownDipSubSectBuilder instance or null if not set
     */
    public DownDipSubSectBuilder getDownDipSSubSectBuilder() {
        return (DownDipSubSectBuilder) section.getProperty(DOWNDIP_BUILDER);
    }

    /**
     * Convenience static accessor for {@link #getDownDipSSubSectBuilder()}.
     *
     * @param section fault section to read from
     * @return stored DownDipSubSectBuilder or null
     */
    public static DownDipSubSectBuilder getDownDipSSubSectBuilder(FaultSection section) {
        return new FaultSectionProperties(section).getDownDipSSubSectBuilder();
    }

    /**
     * Check whether a section is classed as crustal.
     *
     * @param section fault section to check
     * @return true when the section's partition is {@link PartitionPredicate#CRUSTAL}
     */
    public static boolean isCrustal(FaultSection section) {
        return new FaultSectionProperties(section).getPartition() == PartitionPredicate.CRUSTAL;
    }

    /**
     * Check whether a section is classed as subduction (i.e. not crustal).
     *
     * @param section fault section to check
     * @return true when the section is not crustal
     */
    public static boolean isSubduction(FaultSection section) {
        return !isCrustal(section);
    }

    /**
     * Returns a property value as an int.
     *
     * <p>Will throw an IllegalStateException if the value cannot be represented as an int.
     *
     * @param property the property name
     * @return the property value as an int, or null if it is null.
     */
    public Integer getInt(String property) {
        Object value = section.getProperty(property);
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

    /**
     * Copy a selection of known properties from one section to another. Only a subset of properties
     * are copied (partition, original parent/id, domain, TVZ flag, row/col indices).
     *
     * @param from source section to copy from
     * @param to destination section to copy to
     */
    public static void copy(FaultSection from, FaultSection to) {
        FaultSectionProperties propsFrom = new FaultSectionProperties(from);
        FaultSectionProperties propsTo = new FaultSectionProperties(to);
        propsTo.setPartition(propsFrom.getPartition());
        propsTo.setOriginalParent(propsFrom.getOriginalParent());
        propsTo.setOriginalId(propsFrom.getOriginalId());
        propsTo.setDomain(propsFrom.getDomain());
        if (propsFrom.getTvz()) {
            propsTo.setTvz();
        }
        propsTo.setRowIndex(propsFrom.getRowIndex());
        propsTo.setColIndex(propsFrom.getColIndex());
    }

    /**
     * Backfill script for existing rupture set This should work for all crustal, subduction, and
     * joint rupture sets. Ensure to use the correct fault model if there are crustal sections.
     * Crustal sections must come before subduction sections so that section ids line up.
     *
     * <p>Note that not all properties are backfilled. OriginalId, row index, and others are nto
     * backfilled.
     *
     * @throws IOException if reading or writing the rupture set file fails
     * @throws DocumentException if parsing of any XML/GeoJSON documents fails
     */
    @SuppressWarnings("unchecked")
    public static void backfill() throws IOException, DocumentException {

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
        NZSHM22_FaultModels crustalFaultModel = NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ;
        FaultSectionList parentSections = new FaultSectionList();
        crustalFaultModel.fetchFaultSections(parentSections);

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
                props.setDomain(parent.getDomainNo());
                if (parent.getDomainNo().equals(crustalFaultModel.getTvzDomain())) {
                    props.setTvz();
                }
            }
        }

        ruptureSet.write(new File(ruptureSetName + "props4.zip"));
    }

    /**
     * Small runner that executes {@link #backfill()} when the class is run from the command line.
     * Intended for one-off maintenance use.
     *
     * @param args ignored
     * @throws IOException if reading/writing rupture set files fails
     * @throws DocumentException if there is an issue parsing XML/geojson documents
     */
    public static void main(String[] args) throws IOException, DocumentException {
        backfill();
    }
}
