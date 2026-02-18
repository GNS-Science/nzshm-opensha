package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class FaultSectionProperties2 {

    public static final String PARTITION = "Partition";
    public static final String DOMAIN = "Domain";
    public static final String ORIGINAL_PARENT = "OriginalParent";
    public static final String ORIGINAL_ID = "OriginalId";
    public static final String TVZ = "TVZ";

    final GeoJSONFaultSection section;

    public FaultSectionProperties2(FaultSection section) {
        Preconditions.checkArgument(section instanceof GeoJSONFaultSection);
        this.section = (GeoJSONFaultSection) section;
    }

    public void setDomain(String domain) {
        ((GeoJSONFaultSection) section).setProperty(DOMAIN, domain);
    }

    public String getDomain() {
        return (String) get(DOMAIN);
    }

    public void setOriginalParent(int originalParent) {
        ((GeoJSONFaultSection) section).setProperty(ORIGINAL_PARENT, originalParent);
    }

    public Integer getOriginalParent() {
        return getInt(ORIGINAL_PARENT);
    }

    public void setOriginalId(int originalId) {
        ((GeoJSONFaultSection) section).setProperty(ORIGINAL_ID, originalId);
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

    public void setPartition(PartitionPredicate partition) {
        section.setProperty(PARTITION, partition.name());
    }

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
        // Should only be an Integer if the data does not come from json. For example, in tests.
        if (value instanceof Integer) {
            return (Integer) value;
        }
        double dValue = (Double) value;
        Preconditions.checkState(Math.rint(dValue) == dValue);
        return (int) dValue;
    }
}
