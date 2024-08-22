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


public class RsqSimPatchLoader {

    public final static String RSQSIMS_HIKURANGI = "Hikurangi";
    public final static String RSQSIMS_PUYSEGUR = "Puysegar";

    final File zfaultDeepenIn;
    final File znamesDeepenIn;
    final File rupSet;
    final File solution;

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
        String fileName = "C:\\rsqsimsCatalogue\\fromAndyH\\whole_nz_faults_2500_tapered_slip.flt";
        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.NZTM());

        RsqSimPatchLoader loader = new RsqSimPatchLoader(new File(fileName), patchesFile, null, null, null);
        //loader.loadSolutionPolygons();
        List<Patch> patches = loader.loadPatches();
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
        processCanterbury(args);
    }
}
