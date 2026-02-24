package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import java.util.*;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/** A FaultSubsectionCluster for subduction faults. Has some convenience methods. */
public class DownDipFaultSubSectionCluster extends FaultSubsectionCluster {

    public final List<FaultSection> ddSections;

    public DownDipFaultSubSectionCluster(List<? extends FaultSection> subSects) {
        this(subSects, null);
    }

    public DownDipFaultSubSectionCluster(
            List<? extends FaultSection> subSects, Collection<FaultSection> endSects) {
        super(subSects, endSects);
        ddSections = (List<FaultSection>) subSects;
    }

    /**
     * Reverses the cluster per row, preserving the row order.
     *
     * @return the reversed cluster
     */
    @Override
    public DownDipFaultSubSectionCluster reversed() {
        List<FaultSection> newSections = new ArrayList<>();
        List<FaultSection> currentRow = new ArrayList<>();
        int currentRowId = FaultSectionProperties.getRowIndex(ddSections.get(0));

        for (FaultSection section : ddSections) {
            if (section.getSectionId() != currentRowId) {
                Collections.reverse(currentRow);
                newSections.addAll(currentRow);
                currentRow = new ArrayList<>();
                currentRowId = FaultSectionProperties.getRowIndex(section);
            }
            currentRow.add(section);
        }
        if (!currentRow.isEmpty()) {
            Collections.reverse(currentRow);
            newSections.addAll(currentRow);
        }
        return new DownDipFaultSubSectionCluster(newSections, endSects);
    }

    @Override
    public DownDipFaultSubSectionCluster reversed(FaultSection section) {
        throw new RuntimeException("Not implemented");
    }

    public FaultSection first() {
        return ddSections.get(0);
    }

    private FaultSection lastSection = null;

    /**
     * Get the last section in the topmost row.
     *
     * @return the last section in the topmost row
     */
    public FaultSection last() {
        if (lastSection == null) {
            List<FaultSection> trace = getTraceSections();
            lastSection = trace.get(trace.size() - 1);
        }
        return lastSection;
    }

    /**
     * Returns all sections form the topmost row.
     *
     * @return
     */
    public List<FaultSection> getTraceSections() {
        return ddSections.stream()
                .filter(
                        s ->
                                Objects.equals(
                                        FaultSectionProperties.getRowIndex(s),
                                        FaultSectionProperties.getRowIndex(first())))
                .collect(Collectors.toList());
    }
}
