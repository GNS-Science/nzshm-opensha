package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opengis.util.FactoryException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import java.awt.geom.Area;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;


public class RsqSimPatchLoader {

    public final static String RSQSIMS_HIKURANGI = "Hikurangi";
    public final static String RSQSIMS_PUYSEGUR = "Puysegar";

    final File zfaultDeepenIn;
    final File znamesDeepenIn;
    final File rupSet;
    final File solution;
    FaultSystemRupSet loadedRupSet;


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
                             File rupSet,
                             File solution) throws FactoryException {
        this.zfaultDeepenIn = zfaultDeepenIn;
        this.znamesDeepenIn = znamesDeepenIn;
        this.rupSet = rupSet;
        this.solution = solution;
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

    // oakley XXX
    public void addSectionToPatch(Patch patch, FaultSection section) {
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

    public void loadSolutionPolygons() throws IOException {
        FaultSystemRupSet solution = FaultSystemRupSet.load(this.solution);
        polys = solution.getModule(PolygonFaultGridAssociations.class);
    }

    public void writeMappingsToFile(String outputFile) throws IOException {
        List<String> geojsons = new ArrayList<>();

        Map<Integer, List<Patch>> bySection = patches.stream().collect(Collectors.groupingBy(Patch::getNameSectionId));

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

    public static void process_rundir5469(String[] args) throws IOException, FactoryException {
        String fileName = "C:\\rsqsimsCatalogue\\rundir5469\\zfault_Deepen.in";
        String namesFileName = "C:\\rsqsimsCatalogue\\rundir5469\\znames_Deepen.in";
        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip";
        String solutionFileName = "C:\\Users\\user\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NjUzOTY2Mg==.zip";
        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName), new File(solutionFileName));
        loader.loadSolutionPolygons();
        List<Patch> patches = loader.loadPatches();
        loader.loadNames();
        loader.loadRupSet();
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        patches.stream().filter(Objects::nonNull)
                .filter(p -> p.getMaxLat() < -37.41483321429752)
//                .filter(p -> p.parentId==115 || p.parentId == 3)
//                .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR) && p.section != null)
//            .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR))
                .filter(p -> p.zname.equals(RSQSIMS_HIKURANGI))
                .filter(p -> !p.sections.isEmpty())
                .forEach(p -> builder.addFeature(p.toFeature()));
        builder.toJSON("/tmp/hikurangi-rsqsim.geojson");

        SimpleGeoJsonBuilder builder2 = new SimpleGeoJsonBuilder();
        loader.puysegur.forEach(s ->
        {
            FeatureProperties props = builder2.addFaultSectionPerimeter(s.section);
            builder2.setLineColour(props, "red");
        });
        loader.hikurangi.forEach(s ->
        {
            FeatureProperties props = builder2.addFaultSectionPerimeter(s.section);
            builder2.setLineColour(props, "red");

        });
        builder2.toJSON("/tmp/puysegurregions.geojson");

        RsqSimEventLoader eventLoader = new RsqSimEventLoader(new File("C:\\rsqsimsCatalogue\\rundir5469"), loader);
        eventLoader.loadEvents();

        List<RsqSimEventLoader.Event> events = eventLoader.getJointRuptures();

        System.out.println("Total joint ruptures " + eventLoader.events.size() + ", reconstructed joint ruptures " + events.size());

        List<FaultSection> sections = eventLoader.toFaultSections(events.get(100));
        SimpleGeoJsonBuilder builder3 = new SimpleGeoJsonBuilder();

        for (Patch patch : events.get(100).getPatches()) {
            builder3.addFeature(patch.toFeature());
        }

        for (FaultSection section : sections) {
            FeatureProperties props = builder3.addFaultSectionPerimeter(section);
            builder3.setLineColour(props, "blue");
        }
        builder3.toJSON("/tmp/joint-rupture.geojson");
    }

    /**
     * Processes Bruce's rundir5883
     *
     * @param args
     * @throws IOException
     * @throws FactoryException
     */
    public static void process_rundir5883(String[] args) throws IOException, FactoryException {
        String fileName = "C:\\rsqsimsCatalogue\\rundir5883\\zfault_Deepen.in";
        String namesFileName = "C:\\rsqsimsCatalogue\\rundir5883\\znames_Deepen.in";
        String rupSetFileName = "C:\\Users\\user\\Dropbox\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(1).zip";//"C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip";
        String solutionFileName = "C:\\Users\\user\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NjUzOTY2Mg==.zip";
        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName), new File(solutionFileName));
        loader.loadSolutionPolygons();
        List<Patch> patches = loader.loadPatches();
        loader.loadNames();
        loader.loadRupSetNewBruce();

        loader.writeMappingsToFile("/tmp/bruceNewCrustal.geojson");

        SimpleGeoJsonBuilder patchesGeoJson = new SimpleGeoJsonBuilder();
        patches.stream().filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR)).forEach(p -> {
            FeatureProperties props = patchesGeoJson.addFeature(p.toPolygonFeature());
            patchesGeoJson.setPolygonColour(props, "blue");
        });
        patchesGeoJson.toJSON("/tmp/bruceNewPuysegurPatches.geojson");

        SimpleGeoJsonBuilder patchesGeoJson2 = new SimpleGeoJsonBuilder();
        patches.stream().filter(p -> p.zname.equals(RSQSIMS_HIKURANGI)).forEach(p -> {
            FeatureProperties props = patchesGeoJson2.addFeature(p.toPolygonFeature());
            patchesGeoJson2.setPolygonColour(props, "blue");
        });
        patchesGeoJson2.toJSON("/tmp/bruceNewHikurangiPatches.geojson");

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

    public static void processCanterbury(String[] args) throws IOException, FactoryException {

        String mappingsFile = null;
        String rupSetFile = null;
        String outputFile = null;

        String set = "crustal";

        if (set.equals("puysegur")) {
            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\puysegur_discretized_trimmed_dict.json";
            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_2_PUY_15)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0)(1).zip";
            outputFile = "/tmp/puysegur_patchmatches.json";
        } else if (set.equals("hikurangi")) {
            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\hikkerm_discretized_trimmed_dict.json";
            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip";
            outputFile = "/tmp/hikurangi_patchmatches.json";
        } else if (set.equals("crustal")) {
            mappingsFile = "C:\\rsqsimsCatalogue\\fromAndyH\\rsqsim_crustal_discretized_trimmed_dict.json";
            rupSetFile = "C:\\Users\\user\\GNS\\rupture sets\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
            outputFile = "/tmp/crustal_patchmatches.json";
        }

        Map<Integer, List<Integer>> patchids = USMappingsFile.read(mappingsFile);

        String fileName = "C:\\rsqsimsCatalogue\\fromAndyH\\whole_nz_faults_2500_tapered_slip.flt";
        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.NZTM());

        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, null, null, null);
        List<Patch> patches = loader.loadPatches();
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(rupSetFile));

        List<String> geojsons = new ArrayList<>();

        rupSet.getFaultSectionDataList().forEach(section -> {
            SimpleGeoJsonBuilder patchBuilder = new SimpleGeoJsonBuilder();
            FeatureProperties props = patchBuilder.addFaultSectionPolygon(section);
            patchBuilder.setPolygonColour(props, "rgba(255, 0, 0, 0.8)");
            if (patchids.get(section.getSectionId()) == null) {
                System.out.println("no patches for " + section.getSectionId());
            } else {
                patchids.get(section.getSectionId()).forEach(patchId -> {
                    FeatureProperties p = patchBuilder.addFeature(
                            loader.patchLookup.get(patchId + 1).toPolygonFeature());
                    patchBuilder.setPolygonColour(p, "#d5f024");
                });
            }
            geojsons.add(patchBuilder.toJSON());
        });

        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
        out.write("[");
        out.write(String.join(",\n", geojsons));
        out.write("]");
        out.close();

        //loader.loadSolutionPolygons();

//            loader.loadNames();
//            loader.loadRupSet();
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        patches.stream().filter(Objects::nonNull)
                .filter(p -> p.getMaxLat() < -40.41483321429752)
                .filter(p -> p.getMaxLat() > -42.41483321429752)
//                .filter(p -> p.parentId==115 || p.parentId == 3)
//                .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR) && p.section != null)
//            .filter(p -> p.zname.equals(RSQSIMS_PUYSEGUR))
                //.filter(p -> p.zname.equals(RSQSIMS_HIKURANGI))
                //  .filter(p -> !p.sections.isEmpty())
                .forEach(p -> builder.addFeature(p.toFeature()));
        builder.toJSON("/tmp/andy.geojson");
    }

    public static void main(String[] args) throws FactoryException, IOException {
        // process_rundir5469(args);
        process_rundir5883(args);
        //processCanterbury(args);
    }
}
