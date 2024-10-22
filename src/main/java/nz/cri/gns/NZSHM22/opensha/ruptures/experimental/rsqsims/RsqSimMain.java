package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint.ManipulatedClusterRupture;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opengis.util.FactoryException;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RsqSimMain implements Closeable {

    final SourceType sourceType;
    final String runDirVersion;
    final String outputDir;
    final String baseOutputPath;
    final String basePath;

    final BufferedWriter log;

    @Override
    public void close() throws IOException {
        log.close();
    }

    public enum SourceType {
        BRUCE,
        CANTERBURY
    }

    public RsqSimMain(String baseInputDir, String rundirVersion, SourceType sourceType, String baseOutputDir) throws IOException {
        this.sourceType = sourceType;
        this.runDirVersion = rundirVersion;
        this.outputDir = baseOutputDir + "/" + sourceType + "_" + runDirVersion + "/";
        Files.createDirectories(Paths.get(outputDir));
        this.baseOutputPath = outputDir + runDirVersion + "_";
        this.basePath = baseInputDir + "/" + runDirVersion + "/";
        log = new BufferedWriter(new FileWriter(outputDir + "log.txt"));
    }

    public void log(String line) throws IOException {
        System.out.println(line);
        log.write(line);
        log.newLine();
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

    public void process() throws IOException, FactoryException {

        // load and match patches
        RsqSimPatchLoader patchLoader = sourceType == SourceType.BRUCE ?
                RsqSimPatchLoader.getBrucePatches(basePath) :
                RsqSimPatchLoader.getCanterburyPatches(basePath);

        patchLoader.writeDebugMappings(baseOutputPath);

        // ruptures

        RsqSimEventLoader eventLoader = new RsqSimEventLoader(new File(basePath), patchLoader);
        eventLoader.loadEvents();

        List<RsqSimEventLoader.Event> events = eventLoader.getJointRuptures();

        log("Total joint ruptures " + eventLoader.events.size() + ", reconstructed joint ruptures " + events.size());

        List<RsqSimEventLoader.Event> singleCrustalJointRuptures = eventLoader.makeSingleJointRuptures(events);

        log(singleCrustalJointRuptures.size() + " single crustal joint ruptures");

        eventLoader.writeParticipationRates(singleCrustalJointRuptures, patchLoader.loadedRupSet, baseOutputPath);

        CoulombTester tester = new CoulombTester(patchLoader.loadedRupSet, "C:\\tmp\\stiffnessCaches"); // "C:\\Users\\user\\GNS\\rupture sets\\stiffnessCache-nzshm22_complete_merged\\");
        tester.setupStiffness();
        //  List<List<PlausibilityResult>> stiffness = ruptures.parallelStream().map(r -> r.jump).map(tester::applyCoulomb).collect(Collectors.toList());
        //System.out.println("passes: " +stiffness.stream().map(s -> s.get(2).isPass()).filter(p -> p).count());
        List<RsqSimEventLoader.Event> passes = singleCrustalJointRuptures.parallelStream().filter(event -> tester.applyCoulomb(event.jump).get(2).isPass()).collect(Collectors.toList());
        log("passes: " + passes.size());

        log(tester.testSelfStiffnessFilter(singleCrustalJointRuptures));

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

    public static void processBruce5942() throws IOException, FactoryException {
        RsqSimMain main = new RsqSimMain("C:\\rsqsimsCatalogue\\", "rundir5942", SourceType.BRUCE, "/tmp/");
        main.process();
        main.close();
    }

    public static void processCanterbury() throws IOException, FactoryException {
        RsqSimMain main = new RsqSimMain("C:\\rsqsimsCatalogue\\", "fromAndyH", SourceType.CANTERBURY, "/tmp/");
        main.process();
        main.close();
    }

    public static void main(String[] args) throws FactoryException, IOException {
//        processBruce5942();
        processCanterbury();
    }
}
