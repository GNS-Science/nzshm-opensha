package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

public class RsqSimEventLoader {

    final File runDir;
    final RsqSimPatchLoader patchLoader;
    final List<Event> events = new ArrayList<>();

    public RsqSimEventLoader(File runDir, RsqSimPatchLoader patchLoader) {
        this.runDir = runDir;
        this.patchLoader = patchLoader;
    }

    public static class Event {
        public final int id;
        public List<Patch> patches = new ArrayList<>();
        public List<FaultSection> sections;

        public Event(int id) {
            this.id = id;
        }

        public List<Patch> getPatches() {
            return patches;
        }

        boolean isJointRupture() {
            boolean hasSubduction = false;
            boolean hasCrustal = false;
            for (Patch patch : patches) {
                if (patch.zname.equals(RsqSimPatchLoader.RSQSIMS_HIKURANGI) || patch.zname.equals(RsqSimPatchLoader.RSQSIMS_PUYSEGUR)) {
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

        public Map<FaultSection, Integer> getSectionFillCount() {
            Map<FaultSection, Integer> result = new HashMap<>();
            for (Patch patch : patches) {
                for (FaultSection section : patch.sections) {
                    result.compute(section, (key, value) -> Objects.isNull(value) ? 1 : value + 1);
                }
            }
            return result;
        }

    }


    public List<FaultSection> toFaultSections(Event event) {
        List<FaultSection> sections = new ArrayList<>();
        Map<FaultSection, Integer> eventFill = event.getSectionFillCount();
        for (FaultSection section : eventFill.keySet()) {
            double fillRatio = eventFill.get(section) / (double) patchLoader.patchCountPerSection.get(section);
            if (fillRatio > 0.5) {
                sections.add(section);
            }
        }
        return sections;
    }

    public List<Event> getJointRuptures() {
        return events.stream()
                .filter(Event::isJointRupture)
                .peek(event -> event.sections = toFaultSections(event))
                .filter(Event::isOpenShaJointRupture)
                .collect(Collectors.toList());
    }


    public List<Event> loadEvents() throws IOException {

        List<Integer> eList = loadCatalogFile(new File(runDir, ".eList"));
        List<Integer> pList = loadCatalogFile(new File(runDir, ".pList"));

        Preconditions.checkState(eList.size() == pList.size());

        System.out.println("Loaded " + eList.size() + " rows with " + eList.get(eList.size() - 1) + " events");

        Event event = new Event(eList.get(0));

        for (int i = 0; i < eList.size(); i++) {
            int eid = eList.get(i);
            int pid = pList.get(i);
            if (event.id != eid) {
                if (event.isJointRupture()) {
                    events.add(event);
                }
                event = new Event(eid);
            }
            Patch patch = patchLoader.getPatch(pid);
            Preconditions.checkState(Objects.nonNull(patch));
            event.patches.add(patch);
        }

        System.out.println("Identified " + events.size() + " joint ruptures");
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

//    public static void main(String[] args) throws IOException {
//        RsqSimEventLoader loader = new RsqSimEventLoader(null);
//        loader.loadCatalogFile(new File("C:\\rsqsimsCatalogue\\rundir5469\\.eList"));
//
//    }
}
