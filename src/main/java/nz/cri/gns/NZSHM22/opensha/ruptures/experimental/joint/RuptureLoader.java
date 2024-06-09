package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads a rupture set from an archive and restores subduction class like
 * DownDipFaultSection and DownDipFaultSubSectionCluster
 */
public class RuptureLoader {

    protected List<FaultSection> subSections;
    protected Set<Integer> subductionParents;
    protected List<ClusterRupture> ruptures;

    public RuptureLoader() {
    }

    protected void makeSectionList(List<? extends FaultSection> sections) {
        subductionParents = new HashSet<>();
        Pattern pattern = Pattern.compile("; col: (\\d+), row: (\\d+)");
        subSections = sections.stream().map(section -> {
            Matcher m = pattern.matcher(section.getSectionName());
            if (m.find()) {
                DownDipFaultSection ddSection = new DownDipFaultSection();
                ddSection.setFaultSectionPrefData(section);
                ddSection.setColIndex(Integer.parseInt(m.group(1)));
                ddSection.setRowIndex(Integer.parseInt(m.group(2)));
                subductionParents.add(ddSection.getParentSectionId());

                return ddSection;
            }
            return section;

        }).collect(Collectors.toList());
    }

    protected FaultSubsectionCluster translateCluster(FaultSubsectionCluster origCluster) {
        List<FaultSection> sections = origCluster.subSects.stream().map(s -> {
            FaultSection section = subSections.get(s.getSectionId());
            Preconditions.checkState(section.getSectionId() == s.getSectionId());
            Preconditions.checkState(section.getParentSectionId() == s.getParentSectionId());
            return section;
        }).collect(Collectors.toList());

        if (subductionParents.contains(origCluster.parentSectionID)) {
            return new DownDipFaultSubSectionCluster(sections, List.of());
        }
        return new FaultSubsectionCluster(sections, List.of());
    }

    boolean clustersEqual(FaultSubsectionCluster ca, FaultSubsectionCluster cb) {
        if (ca.subSects.size() != cb.subSects.size()) {
            return false;
        }
        for (int i = 0; i < ca.subSects.size(); i++) {
            if (ca.subSects.get(i).getSectionId() != cb.subSects.get(i).getSectionId()) {
                return false;
            }
        }
        return true;
    }

    protected ClusterRupture translateRupture(ClusterRupture origRupture) {
        List<FaultSubsectionCluster> clusters = Arrays.stream(origRupture.clusters).map(this::translateCluster).collect(Collectors.toList());

        List<Jump> jumps = new ArrayList<>();

        for (int i = 1; i < origRupture.clusters.length; i++) {
            Jump origJump = origRupture.internalJumps.get(i - 1);
            FaultSubsectionCluster origFromCluster = origJump.fromCluster;
            FaultSubsectionCluster fromCluster = clusters.get(i - 1);
            Preconditions.checkState(clustersEqual(origFromCluster, fromCluster));
            FaultSubsectionCluster origToCluster = origJump.toCluster;
            FaultSubsectionCluster toCluster = clusters.get(i);
            Preconditions.checkState(clustersEqual(origToCluster, toCluster));

            jumps.add(new Jump(
                    subSections.get(origJump.fromSection.getSectionId()),
                    fromCluster,
                    subSections.get(origJump.toSection.getSectionId()),
                    toCluster,
                    origJump.distance));
        }

        return new ManipulatedClusterRupture(clusters.toArray(FaultSubsectionCluster[]::new), ImmutableList.copyOf(jumps), origRupture.unique);
    }

    /**
     * Loads the ruptures from the specified file and ensures that DownDipFaultSubSectionCluster and
     * DownDipFaultSection are used when appropriate.
     *
     * @param rupSet
     * @throws IOException
     */
    public void loadRuptures(FaultSystemRupSet rupSet) throws IOException {

        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);

        makeSectionList(rupSet.getFaultSectionDataList());

        ruptures = new ArrayList<>();
        for (ClusterRupture rupture : cRups) {
            ruptures.add(translateRupture(rupture));
        }
    }
}
