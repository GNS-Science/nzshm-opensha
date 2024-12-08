package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opengis.util.FactoryException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import java.awt.geom.Area;
import java.io.*;
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

        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        for (Patch patch : patches) {
            if (patch.locations.get(0).lat < -44) {
                builder.addFeature(patch.toPolygonFeature());
            }
        }
        builder.toJSON(baseOutputPath + "patches.geojson");
    }


    /**
     * Processes Bruce's rundir5883
     *
     * @throws IOException
     * @throws FactoryException
     */
    public static RsqSimPatchLoader getBrucePatches(String basePath) throws IOException, FactoryException {

        String fileName = basePath + "zfault_Deepen.in";
        String namesFileName = basePath + "znames_Deepen.in";
        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip";

        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.UTM(59, false));
        RsqSimPatchLoader patchLoader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName));
        patchLoader.loadPatches();
        patchLoader.loadNames();
        patchLoader.loadRupSetNewBruce();

        return patchLoader;
    }

    public static RsqSimPatchLoader getCanterburyPatches(String basePath) throws IOException, FactoryException {
        String fileName = basePath + "whole_nz_faults_2500_tapered_slip.flt";
        String namesFileName = basePath + "znames_Deepen.in";

        String rupSetFileName = "C:\\Users\\user\\GNS\\rupture sets\\nzshm22_complete_merged.zip";

        PatchesFile patchesFile = new PatchesFile(fileName, new CoordinateConverter.NZTM());
        RsqSimPatchLoader patchLoader = new RsqSimPatchLoader(new File(fileName), patchesFile, new File(namesFileName), new File(rupSetFileName));
        patchLoader.loadPatches();

        patchLoader.loadRupSetCanterbury(basePath);

        return patchLoader;
    }


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

        //checkRupSetMatches();
    }
}
