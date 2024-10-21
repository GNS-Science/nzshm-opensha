package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint.ManipulatedClusterRupture;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opengis.util.FactoryException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.awt.geom.Area;
import java.io.*;
import java.nio.BufferOverflowException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class RsqSimPatchLoader {

    public final static String RSQSIMS_HIKURANGI = "Hikurangi";
    public final static String RSQSIMS_PUYSEGUR = "Puysegar";

    final File zfaultDeepenIn;
    final File znamesDeepenIn;
    final File rupSet;
    public FaultSystemRupSet loadedRupSet;

    final PatchesFile patchesFile;

    Map<String, List<FaultSection>> nameToSection;
    PolygonFaultGridAssociations polys;

    List<SubductionSection> hikurangi;
    List<SubductionSection> puysegur;

    public List<Patch> patches = new ArrayList<>();

    public Map<Integer, Patch> patchLookup = new HashMap<>();

    public Patch getPatch(int id) {
        return patchLookup.get(id);
    }

    public static class SubductionSection {

        final Area area;
        public final FaultSection section;

        public SubductionSection(FaultSection section) {
            this.section = section;
            RuptureSurface surf = section.getFaultSurface(1, false, false);
            LocationList locations = surf.getPerimeter();
            area = new Area(locations.toPath());

        }

        public boolean overlaps(Patch patch) {

            if (area.contains(patch.locations.first().lon, patch.locations.first().lat)) {
                return true;
            }
            if (area.contains(patch.locations.get(1).lon, patch.locations.get(1).lat)) {
                return true;
            }
            if (area.contains(patch.locations.last().lon, patch.locations.last().lat)) {
                return true;
            }

            return false;
        }
    }

    public RsqSimPatchLoader(File zfaultDeepenIn,
                             PatchesFile patchesFile,
                             File znamesDeepenIn,
                             File rupSet) throws FactoryException {
        this.zfaultDeepenIn = zfaultDeepenIn;
        this.znamesDeepenIn = znamesDeepenIn;
        this.rupSet = rupSet;
        this.patchesFile = patchesFile;
    }


    public List<Patch> loadPatches() throws IOException {
        patches = patchesFile.loadPatches();
        patches.forEach(p -> patchLookup.put(p.id, p));
        return patches;
    }


    public void loadNames() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(znamesDeepenIn));
        int index = 0;
        String line = null;
        while ((line = reader.readLine()) != null) {
            line = line.substring(4, line.length() - 3);
            patches.get(index).zname = line;
            String[] lineParts = line.split(" ");
            if (lineParts.length > 1) {
                patches.get(index).sectionIdFromZname = Integer.parseInt(lineParts[0]);
            }
            index++;
        }
        Preconditions.checkState(index == patches.size());
        reader.close();
    }

    String shortenName(String name) {

        if (name.length() < 32) {
            return name;
        }
        return name.substring(0, 32).trim();
    }

    public double getDistance(FaultSection section, Patch patch) {
        Region poly = polys.getPoly(section.getSectionId());
        return patch.locations.stream().mapToDouble(poly::distanceToLocation).max().getAsDouble();
    }

    Map<FaultSection, Integer> patchCountPerSection = new HashMap<>();

    public void addSectionToPatch(Patch patch, FaultSection section) {

        // reject patches that are too low or too high
        // only do this if the section has a big enough dip, otherwise it might be too restrictive.
        // only do this for crustal. subduction is modelled too coarsely by Bruce
        if (!section.getSectionName().contains("row:") && section.getAveDip() > 20) {
            boolean keep = false;
            for (Location location : patch.locations) {
                if (location.depth > section.getOrigAveUpperDepth() &&
                        location.depth < section.getAveLowerDepth()) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                return;
            }
        }

        patch.sections.add(section);
        patchCountPerSection.compute(section, (key, value) -> Objects.isNull(value) ? 1 : value + 1);
    }

    public void findSubductionSections(Patch patch) {
        List<SubductionSection> sections = patch.zname.equals(RSQSIMS_HIKURANGI) ? hikurangi : puysegur;

        for (SubductionSection candidate : sections) {
            if (candidate.overlaps(patch)) {
                addSectionToPatch(patch, candidate.section);
            }
        }
    }

    static class LambdaCounter {
        public int count = 0;

        public void inc() {
            count++;
        }

        public String toString() {
            return "" + count;
        }
    }

    /**
     * For Bruce's rundir5883, where crustal patches have the subsection id in the zname
     *
     * @throws IOException
     */
    public void loadRupSetNewBruce() throws IOException {
        loadedRupSet = FaultSystemRupSet.load(this.rupSet);
        hikurangi = new ArrayList<>();
        puysegur = new ArrayList<>();
        // load up the lists needed by findSubductionSections()
        loadedRupSet.getFaultSectionDataList().forEach(
                s -> {
                    if (s.getSectionName().startsWith("Hikurangi")) {
                        hikurangi.add(new SubductionSection(s));
                    }
                    if (s.getSectionName().startsWith("Puysegur")) {
                        puysegur.add(new SubductionSection(s));
                    }
                }
        );

        // crustal sections should be matched directly by id
        patches.forEach(p -> {
            if (p.sectionIdFromZname != -1) {
                addSectionToPatch(p, loadedRupSet.getFaultSectionData(p.sectionIdFromZname));
            }
        });
        //subduction is done geometrically
        patches.stream()
                .filter(p -> p.zname.equals(RSQSIMS_HIKURANGI) || p.zname.equals(RSQSIMS_PUYSEGUR))
                .forEach(this::findSubductionSections);
    }

    void loadRupSetCanterbury(String mappingsFile, int sectionOffset) throws FileNotFoundException {
        Map<Integer, List<Integer>> patchIds = UCMappingsFile.read(mappingsFile, sectionOffset);

        patchIds.keySet().forEach(sectionId -> {
            patchIds.get(sectionId).forEach(patchId -> {
                Patch patch = patches.get(patchId - 1);
                Preconditions.checkState(patch.id == patchId);
                addSectionToPatch(patch, loadedRupSet.getFaultSectionData(sectionId));
            });
        });

    }

    public void loadRupSetCanterbury(String basePath) throws IOException {
        loadedRupSet = FaultSystemRupSet.load(this.rupSet);
        loadRupSetCanterbury(basePath + "rsqsim_crustal_discretized_trimmed_dict.json", 0);
        loadRupSetCanterbury(basePath + "hikkerm_discretized_trimmed_dict.json", 2596);
        loadRupSetCanterbury(basePath + "puysegur_discretized_trimmed_dict.json", 2325);
    }

    /**
     * At this stage, we have basic patch->sections mappings based on section id or others. Now we check that
     */
    public void cleanMappings() {

    }

    public void loadRupSet() throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(this.rupSet);
        nameToSection = new HashMap<>();
        hikurangi = new ArrayList<>();
        puysegur = new ArrayList<>();
        rupSet.getFaultSectionDataList().forEach(
                s -> {
                    String name = shortenName(s.getSectionName());

                    nameToSection.compute(name, (key, old) -> {
                        if (old == null) {
                            old = new ArrayList<>();
                        }
                        old.add(s);
                        return old;
                    });

                    if (s.getSectionName().startsWith("Hikurangi")) {
                        hikurangi.add(new SubductionSection(s));
                    }
                    if (s.getSectionName().startsWith("Puysegur")) {
                        puysegur.add(new SubductionSection(s));
                    }

                }
        );
        Set<String> ghostSections = new HashSet<>();
        LambdaCounter totalSubductionPatches = new LambdaCounter();
        LambdaCounter ghostSubductionPatches = new LambdaCounter();
        patches.forEach(
                p -> {

                    if (p.zname.equals(RSQSIMS_HIKURANGI) || p.zname.equals(RSQSIMS_PUYSEGUR)) {
                        totalSubductionPatches.inc();
                        findSubductionSections(p);
                        return;
                    }

                    List<FaultSection> sections = nameToSection.get(p.zname);

                    if (sections == null || sections.isEmpty()) {
                        ghostSections.add(p.zname);
                        return;
                    }


                    if (sections.size() == 1) {
                        addSectionToPatch(p, sections.get(0));
                    } else {
                        FaultSection nearest = null;
                        double distance = Double.MAX_VALUE;
                        for (FaultSection section : sections) {
                            double d = getDistance(section, p);
                            if (d < distance) {
                                nearest = section;
                                distance = d;
                            }
                        }
                        addSectionToPatch(p, nearest);
                    }


                }
        );

        long matches = patches.stream().filter(p -> !p.sections.isEmpty()).count();
        System.out.println("zname matches: " + matches + " out of " + patches.size());
        long subduction = patches.stream().filter(p ->
                p.sections.isEmpty() && !(p.zname.equals(RSQSIMS_HIKURANGI) || (p.zname.equals(RSQSIMS_PUYSEGUR)))
        ).count();
        System.out.println("crustal without matches: " + subduction);
        System.out.println("total subduction: " + totalSubductionPatches + " ghosts: " + ghostSubductionPatches);
    }

//    public void loadSolutionPolygons() throws IOException {
//        FaultSystemRupSet solution = FaultSystemRupSet.load(this.solution);
//        polys = solution.getModule(PolygonFaultGridAssociations.class);
//    }

    // only works for Bruce's new crustal ids in znames
//    public void writeMappingsToFile(String outputFile) throws IOException {
//        List<String> geojsons = new ArrayList<>();
//
//        Map<Integer, List<Patch>> bySection = patches.stream().collect(Collectors.groupingBy(Patch::getNameSectionId));
//
//        bySection.keySet().forEach(sectionId -> {
//            if (sectionId == -1) {
//                return;
//            }
//            SimpleGeoJsonBuilder patchBuilder = new SimpleGeoJsonBuilder();
//            FeatureProperties props = patchBuilder.addFaultSectionPolygon(loadedRupSet.getFaultSectionData(sectionId));
//            patchBuilder.setPolygonColour(props, "rgba(255, 0, 0, 0.8)");
//            bySection.get(sectionId).forEach(patch -> {
//                FeatureProperties p = patchBuilder.addFeature(
//                        patch.toPolygonFeature());
//                patchBuilder.setPolygonColour(p, "#d5f024");
//            });
//            geojsons.add(patchBuilder.toJSON());
//        });
//
//        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
//        out.write("[");
//        out.write(String.join(",\n", geojsons));
//        out.write("]");
//        out.close();
//    }

    /**
     * Writes two CSV files that contain the mappings between patches and sections.
     * outputFile will be a CSV file that has a section id in the first column and matching patch ids in the following columns.
     * outputFile2 has a patch id in the first column and matching section ids in the following columns.
     *
     * @param outputFile
     * @param outputFile2
     * @throws IOException
     */
    public void writeMappingsToCsv(String outputFile, String outputFile2) throws IOException {

        Map<Integer, List<Patch>> bySection = new HashMap<>();
        patches.forEach(patch -> {
            patch.sections.forEach(section -> {
                bySection.compute(section.getSectionId(), (key, value) -> {
                    if (value == null) {
                        value = new ArrayList<>();
                    }
                    value.add(patch);
                    return value;
                });
            });
        });

        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        bySection.keySet().stream().sorted().forEach(sectionId -> {
            try {
                writer.write(sectionId + ", " + loadedRupSet.getFaultSectionDataList().get(sectionId).getSectionName() + ", ");
                List<Patch> patches = bySection.get(sectionId);
                if (patches != null) {
                    String patchIds = patches.stream().map(p -> p.id + "").collect(Collectors.joining(", "));
                    writer.write(patchIds);
                }
                writer.write("\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writer.close();

        BufferedWriter writer2 = new BufferedWriter(new FileWriter(outputFile2));
        patches.stream().sorted(Comparator.comparing(p -> p.id)).forEach(patch -> {
            try {
                writer2.write(patch.id + ", ");
                String sectionIds = patch.sections.stream().map(s -> s.getSectionId() + "").collect(Collectors.joining(", "));
                writer2.write(sectionIds);
                writer2.write("\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        writer2.close();
    }


    public void writeMappingsToFile(String outputFile, Predicate<FaultSection> sectionFilter) throws IOException {

        Map<Integer, List<Patch>> bySection = new HashMap<>();
        patches.forEach(patch -> {
            patch.sections.forEach(section -> {
                if (sectionFilter.test(section)) {
                    bySection.compute(section.getSectionId(), (key, value) -> {
                        if (value == null) {
                            value = new ArrayList<>();
                        }
                        value.add(patch);
                        return value;
                    });
                }
            });
        });

        List<String> geojsons = new ArrayList<>();
        bySection.keySet().forEach(sectionId -> {
            if (sectionId == -1) {
                return;
            }
            SimpleGeoJsonBuilder patchBuilder = new SimpleGeoJsonBuilder();
            FeatureProperties props = patchBuilder.addFaultSectionPolygon(loadedRupSet.getFaultSectionData(sectionId));
            patchBuilder.setPolygonColour(props, "rgba(255, 0, 0, 0.8)");
            bySection.get(sectionId).forEach(patch -> {
                FeatureProperties p = patchBuilder.addFeature(
                        patch.toPolygonFeature());
                patchBuilder.setPolygonColour(p, "#d5f024");
            });
            geojsons.add(patchBuilder.toJSON());
        });

        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
        out.write("[");
        out.write(String.join(",\n", geojsons));
        out.write("]");
        out.close();
    }

//    public static void process_rundir5469(String[] args) throws IOException, FactoryException {
//        String fileName = "C:\\rsqsimsCatalogue\\rundir5469\\zfault_Deepen.in";
//        String namesFileName = "C:\\rsqsimsCatalogue\\rundir5469\\znames_Deepen.in";
//        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip";
//        String solutionFileName = "C:\\Users\\user\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NjUzOTY2Mg==.zip";
//        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
//        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName), new File(solutionFileName));
//        loader.loadSolutionPolygons();
//        List<Patch> patches = loader.loadPatches();
//        loader.loadNames();
//        loader.loadRupSet();
//        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
//        patches.stream().filter(Objects::nonNull)
//                .filter(p -> p.getMaxLat() < -37.41483321429752)
////                .filter(p -> p.parentId==115 || p.parentId == 3)
////                .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR) && p.section != null)
////            .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR))
//                .filter(p -> p.zname.equals(RSQSIMS_HIKURANGI))
//                .filter(p -> !p.sections.isEmpty())
//                .forEach(p -> builder.addFeature(p.toFeature()));
//        builder.toJSON("/tmp/hikurangi-rsqsim.geojson");
//
//        SimpleGeoJsonBuilder builder2 = new SimpleGeoJsonBuilder();
//        loader.puysegur.forEach(s ->
//        {
//            FeatureProperties props = builder2.addFaultSectionPerimeter(s.section);
//            builder2.setLineColour(props, "red");
//        });
//        loader.hikurangi.forEach(s ->
//        {
//            FeatureProperties props = builder2.addFaultSectionPerimeter(s.section);
//            builder2.setLineColour(props, "red");
//
//        });
//        builder2.toJSON("/tmp/puysegurregions.geojson");
//
//        RsqSimEventLoader eventLoader = new RsqSimEventLoader(new File("C:\\rsqsimsCatalogue\\rundir5469"), loader);
//        eventLoader.loadEvents();
//
//        List<RsqSimEventLoader.Event> events = eventLoader.getJointRuptures();
//
//        System.out.println("Total joint ruptures " + eventLoader.events.size() + ", reconstructed joint ruptures " + events.size());
//
//        List<FaultSection> sections = eventLoader.toFaultSections(events.get(100));
//        SimpleGeoJsonBuilder builder3 = new SimpleGeoJsonBuilder();
//
//        for (Patch patch : events.get(100).getPatches()) {
//            builder3.addFeature(patch.toFeature());
//        }
//
//        for (FaultSection section : sections) {
//            FeatureProperties props = builder3.addFaultSectionPerimeter(section);
//            builder3.setLineColour(props, "blue");
//        }
//        builder3.toJSON("/tmp/joint-rupture.geojson");
//    }

    public static MultiRuptureJump makeJump(List<ClusterRupture> ruptures) {
        return new MultiRuptureJump(
                ruptures.get(0).clusters[0].startSect,
                ruptures.get(0),
                ruptures.get(1).clusters[0].startSect,
                ruptures.get(1),
                5);
    }

    /**
     * Processes Bruce's rundir5883
     *
     * @param args
     * @throws IOException
     * @throws FactoryException
     */
    public static void process_rundir5883() throws IOException, FactoryException {
        process_bruce("rundir5883");
    }

    public void writeDebugMappings(String baseOutputPath) throws IOException {
        writeMappingsToCsv(
                baseOutputPath + "sectionToPatches.csv",
                baseOutputPath + "patchToSections.csv");

        // patches debug files
        writeMappingsToFile(baseOutputPath + "mappingsCrustal.geojson",
                section -> !section.getSectionName().startsWith("Hikurangi") && !section.getSectionName().startsWith("Hikurangi"));

        writeMappingsToFile(baseOutputPath + "mappingsPuysegur.geojson",
                section -> section.getSectionName().startsWith("Puysegur"));

        writeMappingsToFile(baseOutputPath + "mappingsHikurangi.geojson",
                section -> section.getSectionName().startsWith("Hikurangi"));
    }


    public static void writeDebugGeoJSON(List<RsqSimEventLoader.Event> singleCrustalJointRuptures, List<RsqSimEventLoader.Event> passes, String baseOutput) throws IOException {
        List<String> gjs = new ArrayList<>();
        List<String> gjsRupturesOnly = new ArrayList<>();
        List<String> filteredGeoJson = new ArrayList<>();
        Set<RsqSimEventLoader.Event> passFilter = new HashSet<>(passes);
        int ruptureId = 0;
        for (RsqSimEventLoader.Event event : singleCrustalJointRuptures) {// List.of(ruptures.get(0), ruptures.get(1))) {

            boolean isPass = passFilter.contains(event);
            SimpleGeoJsonBuilder builder3 = new SimpleGeoJsonBuilder();

            for (FaultSection section : event.sections) {
                FeatureProperties props = builder3.addFaultSectionPolygon(section);
                builder3.setPolygonColour(props, "red");
                builder3.setLineColour(props, "red");
            }
            if (isPass) {
                gjsRupturesOnly.add(builder3.toJSON());
            }
            for (Patch patch : event.getPatches()) {
                FeatureProperties props = builder3.addFeature(patch.toPolygonFeature());
                builder3.setPolygonColour(props, "green");
            }
            if (isPass) {
                gjs.add(builder3.toJSON());
            }
            if (ruptureId == 264) {
                filteredGeoJson.add(builder3.toJSON());
                System.out.println("event : " + event.id);
            }

            ruptureId++;
        }

        BufferedWriter writer = null;

        writer = new BufferedWriter(new FileWriter(baseOutput + "ruptures.json"));
        List<String> someRuptures = List.of(gjs.get(0), gjs.get(1));
        writer.write("[");
        writer.write(String.join(",\n", someRuptures));
        writer.write("]");
        writer.close();

        writer = new BufferedWriter(new FileWriter(baseOutput + "rupturesOnly.json"));

        writer.write("[");
        writer.write(String.join(",\n", gjsRupturesOnly));
        writer.write("]");
        writer.close();

        writer = new BufferedWriter(new FileWriter(baseOutput + "filteredRuptures.json"));

        writer.write("[");
        writer.write(String.join(",\n", filteredGeoJson));
        writer.write("]");
        writer.close();

    }

    public static void process_rundir5942() throws FactoryException, IOException {
        process_bruce("rundir5942");
    }


    /**
     * Processes Bruce's rundir5883
     *
     * @throws IOException
     * @throws FactoryException
     */
    public static RsqSimPatchLoader getBrucePatches(String runDirVersion) throws IOException, FactoryException {
        String outputDir = "/tmp/bruce_" + runDirVersion + "/";
        Files.createDirectories(Paths.get(outputDir));
        String baseOutputPath = outputDir + runDirVersion + "_";

        String basePath = "C:\\rsqsimsCatalogue\\" + runDirVersion + "\\";
        String fileName = basePath + "zfault_Deepen.in";
        String namesFileName = basePath + "znames_Deepen.in";
        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip";

        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
        RsqSimPatchLoader patchLoader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName));
        patchLoader.loadPatches();
        patchLoader.loadNames();
        patchLoader.loadRupSetNewBruce();

        patchLoader.writeDebugMappings(baseOutputPath);

        return patchLoader;
    }

    /**
     * Processes Bruce's rundir5883
     *
     * @throws IOException
     * @throws FactoryException
     */
    public static void process_bruce(String runDirVersion) throws IOException, FactoryException {

        String outputDir = "/tmp/bruce_" + runDirVersion + "/";
        Files.createDirectories(Paths.get(outputDir));
        String baseOutputPath = outputDir + runDirVersion + "_";

        String basePath = "C:\\rsqsimsCatalogue\\" + runDirVersion + "\\";

        // load and match patches
        RsqSimPatchLoader patchLoader = getBrucePatches(runDirVersion);

        // ruptures

        RsqSimEventLoader eventLoader = new RsqSimEventLoader(new File(basePath), patchLoader);
        eventLoader.loadEvents();

        List<RsqSimEventLoader.Event> events = eventLoader.getJointRuptures();

        System.out.println("Total joint ruptures " + eventLoader.events.size() + ", reconstructed joint ruptures " + events.size());
        SectionDistanceAzimuthCalculator disAzCalc = new SectionDistanceAzimuthCalculator(patchLoader.loadedRupSet.getFaultSectionDataList());

        ClusterAggregator aggregator = new ClusterAggregator(disAzCalc, 5);

        List<RsqSimEventLoader.Event> singleCrustalJointRuptures = events.stream()
                .peek(event -> {
                    List<ClusterRupture> rs = aggregator.makeRuptures(event);
                    if (aggregator.allConnected(rs)) {
                        event.jump = makeJump(rs);
                    }

                })// turn into rupture pairs
                .filter(event -> event.jump != null) // check if there's a single crustal rupture
                .collect(Collectors.toList());

        System.out.println(singleCrustalJointRuptures.size() + " single crustal joint ruptures");

        eventLoader.writeParticipationRates(singleCrustalJointRuptures, patchLoader.loadedRupSet, baseOutputPath);

        CoulombTester tester = new CoulombTester(patchLoader.loadedRupSet, "C:\\tmp\\stiffnessCaches"); // "C:\\Users\\user\\GNS\\rupture sets\\stiffnessCache-nzshm22_complete_merged\\");
        tester.setupStiffness();
        //  List<List<PlausibilityResult>> stiffness = ruptures.parallelStream().map(r -> r.jump).map(tester::applyCoulomb).collect(Collectors.toList());
        //System.out.println("passes: " +stiffness.stream().map(s -> s.get(2).isPass()).filter(p -> p).count());
        List<RsqSimEventLoader.Event> passes = singleCrustalJointRuptures.parallelStream().filter(event -> tester.applyCoulomb(event.jump).get(2).isPass()).collect(Collectors.toList());
        System.out.println("passes: " + passes.size());

        List<ClusterRupture> clusterRuptures = singleCrustalJointRuptures.stream().map(event -> ManipulatedClusterRupture.makeRupture(event.sections)).collect(Collectors.toList());

        FaultSystemRupSet resultRupSet = FaultSystemRupSet.builderForClusterRups(
                        patchLoader.loadedRupSet.getFaultSectionDataList(),
                        clusterRuptures)
                .forScalingRelationship(ScalingRelationships.SHAW_2009_MOD)
                .addModule(tester.stiffness)
                .build();
        resultRupSet.write(new File(baseOutputPath + "rupset.zip"));

        tester.writeStats(singleCrustalJointRuptures, baseOutputPath + "filterStats.csv");

        writeDebugGeoJSON(singleCrustalJointRuptures, passes, baseOutputPath);

    }

    public static void main1(String[] args) throws IOException {
        String fileName = "C:\\Users\\user\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(fileName));
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        rupSet.getFaultSectionDataList().stream()//.filter(s -> s.getParentSectionId() < 100).
                .forEach(s -> {
                    FeatureProperties props = builder.addFaultSection(s);
                    builder.setLineColour(props, "red");
                    builder.setLineWidth(props, 5);
                });
        builder.toJSON("/tmp/nzshm22_red.geojson");
    }

    public static void processCanterburyAll(String runDirVersion) throws IOException, FactoryException {
        String outputDir = "/tmp/andy_" + runDirVersion + "/";
        Files.createDirectories(Paths.get(outputDir));
        String baseOutputPath = outputDir + runDirVersion + "_";

        String basePath = "C:\\rsqsimsCatalogue\\" + runDirVersion + "\\";
        String fileName = basePath + "whole_nz_faults_2500_tapered_slip.flt";
        String namesFileName = basePath + "znames_Deepen.in";

        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip";

        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
        RsqSimPatchLoader patchLoader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName));
        patchLoader.loadPatches();

        patchLoader.loadRupSetCanterbury(basePath);


    }

//    public static void processCanterbury(String[] args) throws IOException, FactoryException {
//
//        String mappingsFile = null;
//        String rupSetFile = null;
//        String outputFile = null;
//
//        String set = "crustal";
//
//        if (set.equals("puysegur")) {
//            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\puysegur_discretized_trimmed_dict.json";
//            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_2_PUY_15)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0)(1).zip";
//            outputFile = "/tmp/puysegur_patchmatches.json";
//        } else if (set.equals("hikurangi")) {
//            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\hikkerm_discretized_trimmed_dict.json";
//            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip";
//            outputFile = "/tmp/hikurangi_patchmatches.json";
//        } else if (set.equals("crustal")) {
//            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\rsqsim_crustal_discretized_trimmed_dict.json";
//            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
//            outputFile = "/tmp/crustal_patchmatches.json";
//        }
//
//        Map<Integer, List<Integer>> patchids = UCMappingsFile.read(mappingsFile);
//
//        String fileName = "C:\\rsqsimsCatalogue\\fromAndyH\\whole_nz_faults_2500_tapered_slip.flt";
//        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.NZTM());
//
//        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, null, null, null);
//        List<Patch> patches = loader.loadPatches();
//        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(rupSetFile));
//
//        List<String> geojsons = new ArrayList<>();
//
//        rupSet.getFaultSectionDataList().forEach(section -> {
//            SimpleGeoJsonBuilder patchBuilder = new SimpleGeoJsonBuilder();
//            FeatureProperties props = patchBuilder.addFaultSectionPolygon(section);
//            patchBuilder.setPolygonColour(props, "rgba(255, 0, 0, 0.8)");
//            if (patchids.get(section.getSectionId()) == null) {
//                System.out.println("no patches for " + section.getSectionId());
//            } else {
//                patchids.get(section.getSectionId()).forEach(patchId -> {
//                    FeatureProperties p = patchBuilder.addFeature(
//                            loader.patchLookup.get(patchId + 1).toPolygonFeature());
//                    patchBuilder.setPolygonColour(p, "#d5f024");
//                });
//            }
//            geojsons.add(patchBuilder.toJSON());
//        });
//
//        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
//        out.write("[");
//        out.write(String.join(",\n", geojsons));
//        out.write("]");
//        out.close();
//
//        //loader.loadSolutionPolygons();
//
////            loader.loadNames();
////            loader.loadRupSet();
//        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
//        patches.stream().filter(Objects::nonNull)
//                .filter(p -> p.getMaxLat() < -40.41483321429752)
//                .filter(p -> p.getMaxLat() > -42.41483321429752)
////                .filter(p -> p.parentId==115 || p.parentId == 3)
////                .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR) && p.section != null)
////            .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR))
//                //.filter(p -> p.zname.equals(RSQSIMS_HIKURANGI))
//                //  .filter(p -> !p.sections.isEmpty())
//                .forEach(p -> builder.addFeature(p.toFeature()));
//        builder.toJSON("/tmp/andy.geojson");
//    }

    public static void checkSectionEquality(List<? extends FaultSection> superSet, List<? extends FaultSection> subSet, int startIndex) {
        for (int i = 0; i < subSet.size(); i++) {
            FaultSection a = superSet.get(i + startIndex);
            FaultSection b = subSet.get(i);
            boolean matches = a.getSectionName().equals(b.getSectionName());
            Preconditions.checkState(matches, i + ": " + (i + startIndex) + " " + a.getSectionName() + " : " + b.getSectionName());
        }
    }

    /**
     * This is to verify that we can use our combined rupset with the Canterbury data.
     *
     * @throws IOException
     */
    public static void checkRupSetMatches() throws IOException {
        FaultSystemRupSet rupSetPuy = FaultSystemRupSet.load(new File("C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_2_PUY_15)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0)(1).zip"));
        FaultSystemRupSet rupSetHik = FaultSystemRupSet.load(new File("C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip"));
        FaultSystemRupSet rupSetCru = FaultSystemRupSet.load(new File("C:\\Users\\user\\GNS\\rupture sets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip"));
        FaultSystemRupSet rupSetCombined = FaultSystemRupSet.load(new File("C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip"));

        Preconditions.checkState(rupSetPuy.getNumSections() + rupSetHik.getNumSections() + rupSetCru.getNumSections() == rupSetCombined.getNumSections());

        int cruStart = 0;
        int hikStart = 2596;
        int puyStart = 2325;

        checkSectionEquality(rupSetCombined.getFaultSectionDataList(), rupSetCru.getFaultSectionDataList(), cruStart);
        checkSectionEquality(rupSetCombined.getFaultSectionDataList(), rupSetPuy.getFaultSectionDataList(), puyStart);
        checkSectionEquality(rupSetCombined.getFaultSectionDataList(), rupSetHik.getFaultSectionDataList(), hikStart);
    }

    public static void main(String[] args) throws FactoryException, IOException {
        //process_rundir5469(args);
        //    process_rundir5883();
        process_rundir5942();
        //processCanterburyAll("fromAndyH");

        //checkRupSetMatches();
    }
}
