package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A rupture set builder that takes a selection of ruptures from one or more rupture sets to create a new rupture set.
 * FaultSections and section parents will be renumbered to suit the new rupture set.
 * The resulting rupture set is bare-bones and intended for joint rupture experimentation.
 * <p>
 * Note: Before adding a rupture or a fault section, the original rupture set has to be set using setRupSet().
 */
public class RuptureAccumulator {

    List<FaultSection> sections = new ArrayList<>();
    List<List<Integer>> sectionForRups = new ArrayList<>();
    List<Double> rakes = new ArrayList<>();
    List<Double> lengths = new ArrayList<>();
    List<Double> mags = new ArrayList<>();
    List<Double> areas = new ArrayList<>();

    Integer nextSectionId = 0;
    Integer nextParentId = 0;

    Map<Integer, Integer> sectionIdMapping;
    Map<Integer, Integer> parentIdMapping;
    Map<ClusterRupture, Integer> ruptureIndex;
    FaultSystemRupSet rupSet;

    /**
     * Sets the rupture set that sections or ruptures can be added from.
     *
     * @param rupSet      a rupture set
     * @param oldRuptures the ruptures from that rupture set.
     * @return this accumulator
     */
    public RuptureAccumulator setRupSet(FaultSystemRupSet rupSet, List<ClusterRupture> oldRuptures) {
        this.rupSet = rupSet;
        sectionIdMapping = new HashMap<>();
        parentIdMapping = new HashMap<>();
        ruptureIndex = new HashMap<>();
        int index = 0;
        for (ClusterRupture rupture : oldRuptures) {
            ruptureIndex.put(rupture, index);
            index++;
        }
        return this;
    }

    /**
     * Add a FaultSection. This happens automatically when a rupture is added. Call this method before adding ruptures
     * if you want to add manipulated FaultSections.
     *
     * @param section the FaultSection
     * @return this accumulator
     */
    public Integer add(FaultSection section) {
        Integer newSectionId = sectionIdMapping.get(section.getSectionId());
        if (newSectionId == null) {
            newSectionId = nextSectionId;
            nextSectionId++;
            sectionIdMapping.put(section.getSectionId(), newSectionId);
            FaultSectionPrefData newSection = new FaultSectionPrefData();
            newSection.setFaultSectionPrefData(section);
            newSection.setSectionId(newSectionId);
            newSection.setParentSectionId(
                    parentIdMapping.computeIfAbsent(
                            section.getParentSectionId(),
                            (k) -> {
                                Integer parentId = nextParentId;
                                nextParentId++;
                                return parentId;
                            }));
            sections.add(newSection);
        }
        return newSectionId;
    }

    /**
     * Adds a rupture. It must be from the currently set rupture set.
     *
     * @param rupture the rupture
     * @return this accumulator
     */
    public RuptureAccumulator add(ClusterRupture rupture) {
        int r = ruptureIndex.get(rupture);
        List<Integer> sections = rupture.buildOrderedSectionList().stream().map(this::add).collect(Collectors.toList());
        sectionForRups.add(sections);
        rakes.add(rupSet.getAveRakeForRup(r));
        lengths.add(rupSet.getLengthForRup(r));
        mags.add(rupSet.getMagForRup(r));
        areas.add(rupSet.getAreaForRup(r));
        return this;
    }

    public RuptureAccumulator addAll(Collection<ClusterRupture> ruptures) {
        ruptures.forEach(this::add);
        return this;
    }

    /**
     * Adds all sections and ruptures from the rupture set.
     *
     * @param rupSet the rupture set
     * @return this accumulator
     */
    public RuptureAccumulator add(FaultSystemRupSet rupSet) {
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranged(rupSet);
        }
        setRupSet(rupSet, cRups.getAll());
        rupSet.getFaultSectionDataList().forEach(this::add);
        addAll(cRups.getAll());
        return this;
    }

    static double[] toDoubleArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Returns a FaultSystemRupSet with all accumulated sections and ruptures.
     *
     * @return the rupture set
     */
    public FaultSystemRupSet build() {
        return FaultSystemRupSet.builder(sections, sectionForRups)
                .rupLengths(toDoubleArray(lengths))
                .rupAreas(toDoubleArray(areas))
                .rupMags(toDoubleArray(mags))
                .rupRakes(toDoubleArray(rakes))
                .build();
    }

    public static void main(String[] args) throws IOException {
        FaultSystemRupSet crustal = FaultSystemRupSet.load(new File("C:\\Users\\user\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(1).zip"));
        FaultSystemRupSet hikurangi = FaultSystemRupSet.load(new File("C:\\Users\\user\\Downloads\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip"));
        FaultSystemRupSet result = new RuptureAccumulator().
                add(crustal).
                add(hikurangi).
                build();
        result.write(new File("/tmp/ruptureset/merged.zip"));
    }
}
