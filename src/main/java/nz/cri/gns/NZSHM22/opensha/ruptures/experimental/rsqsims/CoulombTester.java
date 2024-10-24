package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint.ManipulatedClusterRupture;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.ConcurrentCounter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.StiffnessCalcModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureFractCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureNetCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class CoulombTester implements Closeable {

    String stiffnessCache;
    public FaultSystemRupSet rupSet;
    final SectionDistanceAzimuthCalculator disAzCalc;
    public StiffnessCalcModule stiffness;
    public List<MultiRuptureCoulombFilter> filters = new ArrayList<>();
    BufferedWriter writer = null;

    public CoulombTester(FaultSystemRupSet rupSet, String stiffnessCache) throws IOException {
        this(rupSet, stiffnessCache, null);
    }

    public CoulombTester(FaultSystemRupSet rupSet, String stiffnessCache, File statsFile) throws IOException {
        this.rupSet = rupSet;
        this.stiffnessCache = stiffnessCache;
        this.disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        if (statsFile != null) {
            writer = new BufferedWriter(new FileWriter(statsFile));
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }

    public List<MultiRuptureCoulombFilter> getFilters() {
        return filters;
    }

    public List<MultiRuptureJump> getJumps(FaultSystemRupSet rupSet) {
        ClusterRuptures ruptures = rupSet.requireModule(ClusterRuptures.class);
        return ruptures.getAll().stream().map(ManipulatedClusterRupture::reconstructJump).collect(Collectors.toList());
    }

    public void setupStiffness() {

        stiffness = new StiffnessCalcModule(rupSet, 2, new File(stiffnessCache));

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

    public String testSelfStiffnessFilter(List<RsqSimEventLoader.Event> events) {

        SelfStiffnessCoulombFilter selfStiffness = new SelfStiffnessCoulombFilter(stiffness);

        System.out.println("start");


        ConcurrentCounter selfsub = new ConcurrentCounter();
        ConcurrentCounter selfCru = new ConcurrentCounter();
        ConcurrentCounter selfAll = new ConcurrentCounter();
        ConcurrentCounter selfBoth = new ConcurrentCounter();

        events.parallelStream().forEach(
                event -> {
                    double[] stats = selfStiffness.statsData(event);
                    if(stats[0]>0) {
                        selfsub.inc();
                    }
                    if(stats[1]>0) {
                        selfCru.inc();
                    }
                    if(stats[2]>0) {
                        selfAll.inc();
                    }
                    if(stats[0]>0 && stats[1]>0) {
                        selfBoth.inc();
                    }
                }
        );

        return "all->sub: "+selfsub.get()+" all->cru: "+selfCru.get()+" both: " +selfBoth.get();

    }

    public void writeStats(List<RsqSimEventLoader.Event> events, String fileName) throws IOException {
        int ruptureId = 0;
        SelfStiffnessCoulombFilter selfStiffness = new SelfStiffnessCoulombFilter(stiffness);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("rupture_id, event_id, FractCoulomb_pass, NetCoulomb_pass," + selfStiffness.statsHeader());
            writer.newLine();

            for (RsqSimEventLoader.Event event : events) {
                PlausibilityResult filter0 = filters.get(0).apply(event.jump, false);
                PlausibilityResult filter1 = filters.get(0).apply(event.jump, false);


                writer.write(ruptureId + ", " + event.id + ", " + filter0.isPass() + ", " + filter1.isPass() + ", " + selfStiffness.stats(event));
                writer.newLine();
                ruptureId++;
            }
        }
    }

    public List<PlausibilityResult> applyCoulomb(MultiRuptureJump jump) {
        PlausibilityResult r0 = filters.get(0).apply(jump, false);
        PlausibilityResult r1 = filters.get(1).apply(jump, false);
        PlausibilityResult result = r0.logicalAnd(r1);
//        if(writer != null) {
//            try {
//                int[] stats = filters.get(0).collectStats(jump);
//                for (int stat : stats) {
//                    writer.write(stat + ", ");
//                }
//                writer.write("\n");
//            }catch(IOException x) {
//                throw new RuntimeException(x);
//            }
//        }

        return List.of(r0, r1, result);
    }

    /**
     * helper function to fill the cache with subduction->subduction stiffness
     *
     * @param args
     */
    public static void main(String[] args) throws IOException {
        FaultSystemRupSet rupSet1 = FaultSystemRupSet.load(new File("C:\\Users\\user\\GNS\\science-playground\\WORKDIR\\rupSetBruceRundir5883.zip"));
        //  CoulombTester tester = new CoulombTester(rupSet1, "C:\\Users\\user\\GNS\\science-playground\\WORKDIR\\stiffnessCaches");
        CoulombTester tester = new CoulombTester(rupSet1, "/tmp/stiffnessCaches");
        tester.setupStiffness();
        AggregatedStiffnessCalculator aggCalc = tester.getFilters().get(1).getAggCalc();

        List<List<? extends FaultSection>> sections = rupSet1.getFaultSectionDataList().stream().filter(s -> s.getSectionName().startsWith("Puysegur")).map(List::of).collect(Collectors.toList());

        System.out.println("section count: " + sections.size());

        sections.stream().parallel().forEach(section -> {
            sections.forEach(fromSection -> {
                aggCalc.calc(fromSection, section);
            });
            System.out.print(".");
        });

        tester.saveCache();


    }
}
