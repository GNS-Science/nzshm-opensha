package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
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
}
