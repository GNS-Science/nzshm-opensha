package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

public class RsqSimEventLoader {

    final File runDir;
    final RsqSimPatchLoader patchLoader;
    final List<Event> jointEvents = new ArrayList<>();
    final List<Event> events = new ArrayList<>();

    public RsqSimEventLoader(File runDir, RsqSimPatchLoader patchLoader) {
        this.runDir = runDir;
        this.patchLoader = patchLoader;
    }

    public static class Event {
        public final int id;
        public List<Patch> patches = new ArrayList<>();
        public List<FaultSection> sections;
        public MultiRuptureJump jump;

        public Event(int id) {
            this.id = id;
        }

        public List<Patch> getPatches() {
            return patches;
        }

        /**
         * Returns true if the event has subduction and crustal patches.
         *
         * @return
         */
        boolean isJointRupture() {
            boolean hasSubduction = false;
            boolean hasCrustal = false;
            for (Patch patch : patches) {
                if(!patch.sections.isEmpty() && patch.sections.get(0).getSectionName().contains("row:")){
                    hasSubduction = true;
                } else {
                    hasCrustal = true;
                }
                if (hasCrustal && hasSubduction) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns true if the rupture has crustal and subduction sections
         *
         * @return
         */
        boolean isOpenShaJointRupture() {
            boolean hasSubduction = false;
            boolean hasCrustal = false;
            for (FaultSection section : sections) {
                if (section.getSectionName().contains("row:")) {
                    hasSubduction = true;
                } else {
                    hasCrustal = true;
                }
                if (hasCrustal && hasSubduction) {
                    return true;
                }
            }
            return false;
        }

        public Map<FaultSection, Double> getSectionFillArea() {
            Map<FaultSection, Double> result = new HashMap<>();
            for (Patch patch : patches) {
                for (FaultSection section : patch.sections) {
                    result.compute(section, (key, value) -> patch.area + (Objects.isNull(value) ? 0 : value));
                }
            }
            return result;
        }

    }

    public List<FaultSection> toFaultSections(Event event) {
        List<FaultSection> sections = new ArrayList<>();
        Map<FaultSection, Double> fillArea = event.getSectionFillArea();
        for (FaultSection section : fillArea.keySet()) {
            double fillRatio = fillArea.get(section) / section.getArea(false);
            if (fillRatio > 0.5) {
                sections.add(section);
            }
        }
        return sections;
    }

    public List<Event> getJointRuptures() {
        return jointEvents.stream()
                .filter(Event::isJointRupture)
                .peek(event -> event.sections = toFaultSections(event))
                .filter(Event::isOpenShaJointRupture)
                .collect(Collectors.toList());
    }

    public static MultiRuptureJump makeJump(List<ClusterRupture> ruptures) {
        return new MultiRuptureJump(
                ruptures.get(0).clusters[0].startSect,
                ruptures.get(0),
                ruptures.get(1).clusters[0].startSect,
                ruptures.get(1),
                5);
    }

    /**
     * Filters input down to single crustal joint ruptures.
     * Side effect: event.jump is populated.
     * @param events
     * @return
     */
    public List<Event> makeSingleJointRuptures(List<Event> events) {
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

        return singleCrustalJointRuptures;
    }

    public static File findFile(File path, String... candidateNames) throws FileNotFoundException {
        for (String fileName : candidateNames) {
            File file = new File(path, fileName);
            if (file.exists()) {
                return file;
            }
        }
        throw new FileNotFoundException("Could not find candidate files in " + path + " first candidate: " + candidateNames[0]);
    }


    public List<Event> loadEvents() throws IOException {

        File eListFile = findFile(runDir, ".eList", "eList", "whole_nz.eList");
        File pListFile = findFile(runDir, ".pList", "pList", "whole_nz.pList");

        List<Integer> eList = loadCatalogFile(eListFile);
        List<Integer> pList = loadCatalogFile(pListFile);

        Preconditions.checkState(eList.size() == pList.size());

        System.out.println("Loaded " + eList.size() + " rows with " + eList.get(eList.size() - 1) + " events");

        Event event = new Event(eList.get(0));

        for (int i = 0; i < eList.size(); i++) {
            int eid = eList.get(i);
            int pid = pList.get(i);
            if (event.id != eid) {
                events.add(event);
                if (event.isJointRupture()) {
                    jointEvents.add(event);
                }
                event = new Event(eid);
            }
            Patch patch = patchLoader.getPatch(pid);
            Preconditions.checkState(Objects.nonNull(patch));
            event.patches.add(patch);
        }

        System.out.println("Identified " + jointEvents.size() + " joint ruptures");
        return events;
    }

    public List<Integer> loadCatalogFile(File file) throws IOException {
        List<Integer> result = new ArrayList<>();

        FileInputStream in = new FileInputStream(file);

        byte[] data = in.readAllBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();

        while (buffer.hasRemaining()) {
            result.add(buffer.getInt());
        }

        in.close();

        return result;
    }

    Map<Integer, Integer> calculateParticipation(List<RsqSimEventLoader.Event> events) {
        Map<Integer, Integer> result = new HashMap<>();
        for (RsqSimEventLoader.Event event : events) {
            for (FaultSection section : event.sections) {
                result.compute(section.getSectionId(), (key, value) -> value == null ? 1 : value + 1);
            }
        }
        return result;
    }

    /**
     * writes debug GeoJSON files to show how often each section participates in a rupture.
     *
     * @param events
     * @param rupSet
     */
    public void writeParticipationRates(List<RsqSimEventLoader.Event> events, FaultSystemRupSet rupSet, String baseOutput) {
        Map<Integer, Integer> participation = calculateParticipation(events);
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            FeatureProperties props = builder.addFaultSectionPolygon(section);
            Integer part = participation.get(section.getSectionId());
            props.set("participation", Objects.nonNull(part) ? part : 0);
        }
        builder.toJSON(baseOutput + "participation.geojson");

        builder = new SimpleGeoJsonBuilder();
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            if (!section.getSectionName().contains("row:")) {
                Integer part = participation.get(section.getSectionId());

                if (part != null && part > 0) {

                    FeatureProperties props = builder.addFaultSectionPolygon(section);

                    props.set("participation", part);
                }
            }
        }
        builder.toJSON(baseOutput + "participationCrustal.geojson");
    }


}
