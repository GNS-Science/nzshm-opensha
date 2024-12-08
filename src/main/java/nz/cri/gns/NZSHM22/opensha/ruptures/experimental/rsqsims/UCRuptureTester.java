package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.StiffnessCalcModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureFractCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureNetCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import javax.sound.midi.SysexMessage;
import java.io.*;
import java.util.*;
import java.util.function.Function;

public class UCRuptureTester {

    final String rupFileName;
    final FaultSystemRupSet rupSet;
    final SectionDistanceAzimuthCalculator disAzCalc;

    final double maxInternalJumpDist = 5;

    StiffnessCalcModule stiffness;

    Map<String, MultiRuptureJump> ruptures = new HashMap<>();
    int crustalEnd = 0;
    int hikurangiStart = -1;
    int hikurangiEnd = 0;
    int puysegurStart = -1;
    int puysegurEnd = 0;

    List<MultiRuptureCoulombFilter> filters = new ArrayList<>();

    public UCRuptureTester(String rupFileName) throws IOException {
        this.rupFileName = rupFileName;
        this.rupSet = FaultSystemRupSet.load(new File(rupFileName));
        this.disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        for (int i = 0; i < rupSet.getNumSections(); i++) {
            FaultSection section = rupSet.getFaultSectionData(i);
            if (section.getSectionName().startsWith("Puysegur") && section.getSectionName().contains("row:") && puysegurStart == -1) {
                puysegurStart = i;
            }

            if (section.getSectionName().startsWith("Hikurangi") && section.getSectionName().contains("row:") && hikurangiStart == -1) {
                hikurangiStart = i;
            }
        }
        if (hikurangiStart < puysegurStart) {
            crustalEnd = hikurangiStart - 1;
            hikurangiEnd = puysegurStart - 1;
            puysegurEnd = rupSet.getNumSections() - 1;
        } else {
            crustalEnd = puysegurStart - 1;
            puysegurEnd = hikurangiStart - 1;
            hikurangiEnd = rupSet.getNumSections() - 1;
        }
    }

    Integer getSectionId(String ruptureId, String source, String id) {
        String key = source.toLowerCase().trim();
        int sectionId = Integer.parseInt(id);
        if (key.startsWith("crustal")) {
            Preconditions.checkArgument(sectionId <= crustalEnd, "rupture: " + ruptureId + " source: " + source + " id: " + id + " was over " + crustalEnd);
            return sectionId;
        }
        if (key.startsWith("puysegur")) {
            Preconditions.checkArgument(sectionId <= puysegurEnd);
            return sectionId + puysegurStart;
        }
        if (key.startsWith("hikurangi")) {
            Preconditions.checkArgument(sectionId <= hikurangiEnd);
            return sectionId + hikurangiStart;
        }
        throw new RuntimeException("Unexpected source " + source);
    }

    protected MultiRuptureJump makeJump(ClusterRupture nucleation, ClusterRupture target) {
        for (FaultSection targetSection : target.buildOrderedSectionList()) {
            for (FaultSection nucleationSection : nucleation.clusters[0].subSects) {
                double distance = disAzCalc.getDistance(targetSection, nucleationSection);
                System.out.println(distance);
                return new MultiRuptureJump(nucleation.clusters[0].startSect, nucleation, target.clusters[0].startSect, target, distance);
            }
        }
        return null;
    }


    void loadCSV(String fileName) throws IOException {
        ClusterAggregator aggregator = new ClusterAggregator(disAzCalc, 5);
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        String currentRuptureId = null;
        List<ClusterRupture> currentRuptures = new ArrayList<>();
        int totalCount = 0;
        int jointCount = 0;
        while (true) {
            String line = reader.readLine();
            String[] components = line != null ? line.trim().split("\\h") : new String[]{null};
            String ruptureId = components[0];
            if (!Objects.equals(ruptureId, currentRuptureId)) {
                if(currentRuptureId != null) {
                    totalCount++;
                }
                if(currentRuptureId != null
                        && currentRuptures.size() > 1) {
                    jointCount++;
                }

                if (currentRuptureId != null
                        && currentRuptures.size() > 1
                        && aggregator.allConnected(currentRuptures)) {
                    Preconditions.checkState(currentRuptures.size() == 2);
                    ruptures.put(currentRuptureId, makeJump(currentRuptures.get(0), currentRuptures.get(1)));
                }
                currentRuptureId = ruptureId;
                currentRuptures = new ArrayList<>();
            }
            if (currentRuptureId == null) {
                break;
            }
            String source = components[1];
            List<FaultSection> clusterSections = new ArrayList<>();
            for (int i = 2; i < components.length; i++) {
                clusterSections.add(rupSet.getFaultSectionData(getSectionId(currentRuptureId, source, components[i])));
            }
            ClusterRupture rupture = ClusterRupture.forOrderedSingleStrandRupture(clusterSections, disAzCalc);
            currentRuptures.add(rupture);
        }

        System.out.println("total " + totalCount + " joint count " + jointCount);
        reader.close();
    }

    void setupStiffness() {

        stiffness = new StiffnessCalcModule(
                rupSet,
                2,
                new File("C:\\Users\\user\\GNS\\rupture sets\\stiffnessCache-nzshm22_complete_merged\\"));


        // what fraction of interactions should be positive? this number will take some tuning
        float fractThreshold = 0.75f;
        MultiRuptureFractCoulombPositiveFilter fractCoulombFilter = new MultiRuptureFractCoulombPositiveFilter(stiffness.stiffnessCalc, fractThreshold, ParentCoulombCompatibilityFilter.Directionality.BOTH);
        filters.add(fractCoulombFilter);

        // force the net coulomb from one rupture to the other to positive; this more heavily weights nearby interactions
        ParentCoulombCompatibilityFilter.Directionality netDirectionality = ParentCoulombCompatibilityFilter.Directionality.BOTH; // require it to be positive to from subduction to crustal AND from crustal to subduction
//     	Directionality netDirectionality = Directionality.EITHER; // require it to be positive to from subduction to crustal OR from crustal to subduction
        MultiRuptureNetCoulombPositiveFilter netCoulombFilter = new MultiRuptureNetCoulombPositiveFilter(stiffness.stiffnessCalc, netDirectionality);
        filters.add(netCoulombFilter);
    }

    public void saveCache() {
        stiffness.checkUpdateStiffnessCache();
    }

    public static void main(String[] args) throws IOException {
        UCRuptureTester ruptureTester = new UCRuptureTester("C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip");
        ruptureTester.loadCSV("C:\\Users\\user\\GNS\\RSQSim\\AndyHowellAugust24\\biggest_events.txt");
        System.out.println(ruptureTester.ruptures.size());
        ruptureTester.setupStiffness();

        BufferedWriter writer = new BufferedWriter(new FileWriter("/tmp/UCRSQSimout.csv"));

        int count = 0;
        int passCount = 0;
        for (String ruptureId : ruptureTester.ruptures.keySet()) {
            MultiRuptureJump jump = ruptureTester.ruptures.get(ruptureId);
            PlausibilityResult r0 = ruptureTester.filters.get(0).apply(jump, false);
            PlausibilityResult r1 = ruptureTester.filters.get(1).apply(jump, false);
            PlausibilityResult result = r0.logicalAnd(r1);
            if (result.canContinue()) {
                passCount++;
            }
            writer.write(ruptureId + ", " + result + ", " + r0 + ", " + r1 + "\n");
            count++;
            System.out.println(count + " of " + ruptureTester.ruptures.size());
        }
        writer.close();

        System.out.println(passCount);

        MultiRuptureJump[] jumps = ruptureTester.ruptures.values().toArray(new MultiRuptureJump[0]);
        PlausibilityResult r0 = ruptureTester.filters.get(0).apply(jumps[10], true);
        PlausibilityResult r1 = ruptureTester.filters.get(1).apply(jumps[10], true);

        ruptureTester.saveCache();

        System.out.println("--------------");
        System.out.println(jumps[10].distance);
        System.out.println(r0);
        System.out.println(r1);

        MultiRuptureJump jump = ruptureTester.ruptures.get("2254817");

        SimpleGeoJsonBuilder geoJson = new SimpleGeoJsonBuilder();
        for (FaultSection section : jump.fromRupture.buildOrderedSectionList()) {
            geoJson.addFaultSectionPolygon(section);
        }
        for (FaultSection section : jump.toRupture.buildOrderedSectionList()) {
            geoJson.addFaultSectionPolygon(section);
        }
        geoJson.toJSON("/tmp/rsqsimRup2254817.geojson");
    }
}
