package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * A miniature joint solution shared by the joint hazard tests: two crustal sections and two
 * subduction interface sections near Wellington, with a crustal-only, an interface-only and a joint
 * rupture. Magnitudes are derived from the rupture surface areas with the same scaling that {@link
 * JointRuptureExperimentalIMR} assumes, which is what a real joint solution built with the
 * JOIN_ESTIMATE scaling relationship does.
 */
class JointTestSolutions {

    static final List<List<Integer>> SECTIONS_FOR_RUPS =
            List.of(
                    List.of(0, 1), // crustal
                    List.of(2, 3), // interface
                    List.of(0, 1, 2, 3)); // joint

    static final int CRUSTAL_RUP = 0;
    static final int INTERFACE_RUP = 1;
    static final int JOINT_RUP = 2;

    /**
     * Rate of the ruptures in the single-rupture solutions. High enough that the hazard at the site
     * exceeds the map return periods, so a map built from them has non-zero values.
     */
    static final double SINGLE_RUPTURE_RATE = 1e-2;

    /** A site sitting between the crustal and the interface sections. */
    static final Location SITE = new Location(-41.4, 174.85);

    private JointTestSolutions() {}

    private static GeoJSONFaultSection makeSection(
            int id, TectonicRegionType trt, double lat1, double lon1, double lat2, double lon2) {
        FaultTrace trace = new FaultTrace("trace " + id);
        trace.add(new Location(lat1, lon1));
        trace.add(new Location(lat2, lon2));
        FaultSectionPrefData pref = new FaultSectionPrefData();
        pref.setSectionId(id);
        pref.setSectionName("Section " + id);
        pref.setFaultTrace(trace);
        pref.setAveSlipRate(10);
        pref.setAveRake(trt == TectonicRegionType.ACTIVE_SHALLOW ? 180 : 90);
        pref.setAveDip(trt == TectonicRegionType.ACTIVE_SHALLOW ? 90 : 20);
        pref.setAveUpperDepth(0);
        pref.setAveLowerDepth(trt == TectonicRegionType.ACTIVE_SHALLOW ? 15 : 20);
        pref.setDipDirection((float) trace.getDipDirection());
        GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
        section.setTectonicRegionType(trt);
        new FaultSectionProperties(section)
                .setPartition(
                        trt == TectonicRegionType.ACTIVE_SHALLOW
                                ? PartitionPredicate.CRUSTAL
                                : PartitionPredicate.HIKURANGI);
        return section;
    }

    static List<FaultSection> makeSections() {
        List<FaultSection> sections = new ArrayList<>();
        // two crustal sections, striking NE, west of the site
        sections.add(makeSection(0, TectonicRegionType.ACTIVE_SHALLOW, -41.5, 174.6, -41.4, 174.7));
        sections.add(makeSection(1, TectonicRegionType.ACTIVE_SHALLOW, -41.4, 174.7, -41.3, 174.8));
        // two interface sections, east of the site
        sections.add(
                makeSection(
                        2, TectonicRegionType.SUBDUCTION_INTERFACE, -41.4, 174.9, -41.3, 175.0));
        sections.add(
                makeSection(
                        3, TectonicRegionType.SUBDUCTION_INTERFACE, -41.3, 175.0, -41.2, 175.1));
        return sections;
    }

    /**
     * Builds the rupture set twice: the first pass gives us rupture surfaces to measure areas on,
     * the second sets the magnitudes that those areas imply under the joint scaling.
     */
    static FaultSystemRupSet makeRupSet(double jointMagOffset) {
        List<FaultSection> sections = makeSections();
        double[] placeholderMags = new double[SECTIONS_FOR_RUPS.size()];
        Arrays.fill(placeholderMags, 7d);
        FaultSystemRupSet firstPass =
                FaultSystemRupSet.builder(sections, SECTIONS_FOR_RUPS)
                        .rupMags(placeholderMags)
                        .build();

        double[] mags = new double[SECTIONS_FOR_RUPS.size()];
        for (int r = 0; r < mags.length; r++) {
            double crustalArea = 0;
            double interfaceArea = 0;
            for (int s : SECTIONS_FOR_RUPS.get(r)) {
                double area =
                        firstPass
                                .getFaultSectionData(s)
                                .getFaultSurface(1d, false, false)
                                .getArea();
                if (firstPass.getFaultSectionData(s).getTectonicRegionType()
                        == TectonicRegionType.ACTIVE_SHALLOW) {
                    crustalArea += area;
                } else {
                    interfaceArea += area;
                }
            }
            if (crustalArea > 0 && interfaceArea > 0) {
                mags[r] =
                        JointRuptureExperimentalIMR.getJointMag(crustalArea, interfaceArea)
                                + jointMagOffset;
            } else if (crustalArea > 0) {
                mags[r] = JointRuptureExperimentalIMR.getCrustalMag(crustalArea);
            } else {
                mags[r] = JointRuptureExperimentalIMR.getInterfaceMag(interfaceArea);
            }
        }

        return FaultSystemRupSet.builder(makeSections(), SECTIONS_FOR_RUPS).rupMags(mags).build();
    }

    static FaultSystemSolution makeSolution() {
        return makeSolution(0d);
    }

    /**
     * A solution holding only the crustal rupture, on the two crustal sections. Sections are
     * rebuilt so that this solution shares nothing with its subduction counterpart.
     */
    static FaultSystemSolution makeCrustalSolution() {
        return makeSingleRuptureSolution(List.of(0, 1), true);
    }

    /** A solution holding only the interface rupture, on the two subduction sections. */
    static FaultSystemSolution makeSubductionSolution() {
        return makeSingleRuptureSolution(List.of(2, 3), false);
    }

    /**
     * A crustal solution as it was saved before fault section properties existed: no tectonic
     * region types and no partitions. {@link
     * nz.cri.gns.NZSHM22.opensha.scripts.RupSetPropertyBackfill} recognises such sections as
     * crustal because their names carry no subduction column/row.
     */
    static FaultSystemSolution makeLegacyCrustalSolution() {
        return stripProperties(makeCrustalSolution(), null);
    }

    /**
     * A subduction solution as it was saved before fault section properties existed. The backfill
     * recognises subduction sections by the column and row in their names.
     */
    static FaultSystemSolution makeLegacySubductionSolution() {
        return stripProperties(makeSubductionSolution(), "Hikurangi, Subduction Interface");
    }

    /**
     * Removes the tectonic region type and the partition from every section, and optionally renames
     * the sections to the subduction naming that the backfill keys off.
     *
     * @param sectionName base name for subduction sections, or null to leave names alone
     */
    private static FaultSystemSolution stripProperties(
            FaultSystemSolution solution, String sectionName) {
        List<? extends FaultSection> sections = solution.getRupSet().getFaultSectionDataList();
        for (int s = 0; s < sections.size(); s++) {
            GeoJSONFaultSection section = (GeoJSONFaultSection) sections.get(s);
            section.setTectonicRegionType(null);
            section.getProperties().remove(FaultSectionProperties.PARTITION);
            if (sectionName != null) {
                section.setSectionName(sectionName + "; col: " + s + ", row: 0");
            }
        }
        return solution;
    }

    private static FaultSystemSolution makeSingleRuptureSolution(
            List<Integer> sectionIndices, boolean crustal) {
        List<FaultSection> all = makeSections();
        List<FaultSection> sections = new ArrayList<>();
        for (int i = 0; i < sectionIndices.size(); i++) {
            FaultSection section = all.get(sectionIndices.get(i));
            // sections must be indexed from 0 within their own rupture set
            section.setSectionId(i);
            sections.add(section);
        }
        List<List<Integer>> sectionsForRups = List.of(List.of(0, 1));

        double area = 0;
        FaultSystemRupSet firstPass =
                FaultSystemRupSet.builder(sections, sectionsForRups)
                        .rupMags(new double[] {7d})
                        .build();
        for (int s = 0; s < sections.size(); s++) {
            area += firstPass.getFaultSectionData(s).getFaultSurface(1d, false, false).getArea();
        }
        double mag =
                crustal
                        ? JointRuptureExperimentalIMR.getCrustalMag(area)
                        : JointRuptureExperimentalIMR.getInterfaceMag(area);

        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(sections, sectionsForRups)
                        .rupMags(new double[] {mag})
                        .build();
        return new FaultSystemSolution(rupSet, new double[] {SINGLE_RUPTURE_RATE});
    }

    /**
     * A single solution holding a crustal and an interface rupture but no joint rupture: the case
     * that {@link JointHazardInput.GmmMode#PER_TECTONIC_REGION} exists for.
     */
    static FaultSystemSolution makeMixedSolution() {
        List<List<Integer>> sectionsForRups = List.of(List.of(0, 1), List.of(2, 3));
        FaultSystemRupSet full = makeRupSet(0d);
        double[] mags = {full.getMagForRup(CRUSTAL_RUP), full.getMagForRup(INTERFACE_RUP)};
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(makeSections(), sectionsForRups).rupMags(mags).build();
        return new FaultSystemSolution(
                rupSet, new double[] {SINGLE_RUPTURE_RATE, SINGLE_RUPTURE_RATE});
    }

    static FaultSystemSolution makeSolution(double jointMagOffset) {
        FaultSystemRupSet rupSet = makeRupSet(jointMagOffset);
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, 1e-3);
        return new FaultSystemSolution(rupSet, rates);
    }
}
