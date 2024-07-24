package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.util.*;

/**
 * Loads geometry as specified in https://zenodo.org/records/5534462
 * <p>
 * Geometry File (ASCII): zfault_Deepen.in
 * <p>
 * ASCII file listing patch (triangular) geometry for the simulated faults
 * in a UTM coordinate system (zone 11S). The primary columns are:
 * <p>
 * * /x1, y1, z1/ - UTM coordinates of the first vertex
 * * /x2, y2, z2/ - UTM coordinates of the second vertex
 * * /x3, y3, z3/ - UTM coordinates of the third vertex
 * * /rake/ - Direction of the motion of the hanging wall relative to the
 * footwall (in degrees, following the convention of Aki & Richards, 2002)
 * * /slip_rate/ - Long-term average slip rate (in m/s)
 * <p>
 * The first line in the file (excluding comment lines that start with '#')
 * is the patch with ID=1, the second ID=2, etc. Additional metadata
 * columns may exist in each line beyond those listed and can be ignored.
 */

public class RsqSimsLoader {

    final File zfaultDeepenIn;
    final File znamesDeepenIn;
    final File rupSet;
    final File solution;
    int nextId = 0;

    Map<String, List<FaultSection>> nameToSection;
    PolygonFaultGridAssociations polys;

    List<Patch> patches;

    public static class Patch {
        public final LocationList locations;
        public final int id;
        public final double rake;
        public final double slip;

        public String zname;

        public FaultSection section = null;

        public double getMaxLat() {
            return locations.stream().mapToDouble(l -> l.lat).max().getAsDouble();
        }

        public Patch(int id, LocationList locations, double rake, double slip) {
            this.id = id;
            this.rake = rake;
            this.slip = slip;
            this.locations = locations;
        }

        public static Patch create(int id, Location a, Location b, Location c, double rake, double slip) {
            LocationList locs = new LocationList();
            locs.add(a);
            locs.add(b);
            locs.add(c);
            return new Patch(id, locs, rake, slip);
        }

        public Feature toFeature() {
            LocationList list = new LocationList();
            list.addAll(locations);
            list.add(locations.first());
            Geometry geometry = new Geometry.LineString(list);
            FeatureProperties properties = new FeatureProperties();
            properties.set("rake", rake);
            properties.set("slip", slip);
            return new Feature(id, geometry, new FeatureProperties());
        }

    }

    public RsqSimsLoader(File zfaultDeepenIn, File znamesDeepenIn, File rupSet, File solution) {
        this.zfaultDeepenIn = zfaultDeepenIn;
        this.znamesDeepenIn = znamesDeepenIn;
        this.rupSet = rupSet;
        this.solution = solution;
    }

    static Location toLatLon(double easting, double northing, double depth) {
        Location loc = UtmConverter.convertToLatLng(easting, northing, 59, true);
        return new Location(loc.lat, loc.lon, Math.abs(depth) / 1000);
    }

    Patch loadGeometry(String line) {
        String[] parts = line.split(" ");
        Preconditions.checkArgument(parts.length >= 11, "Line must have at least 11 elements");
        double[] values = new double[11];
        for (int i = 0; i < 11; i++) {
            Preconditions.checkArgument(!parts[i].isEmpty());
            values[i] = Double.parseDouble(parts[i]);
        }

        Location l1 = toLatLon(values[0], values[1], values[2]);
        Location l2 = toLatLon(values[3], values[4], values[5]);
        Location l3 = toLatLon(values[6], values[7], values[8]);
        Patch patch = null;
        try {
            patch = Patch.create(nextId, l1, l2, l3, values[9], values[10]);
        } catch (Exception x) {
            //x.printStackTrace();
            System.err.println("error at line " + nextId + " " + x.getMessage());
        }
        nextId++;
        return patch;
    }

    public List<Patch> loadGeometry() throws IOException {
        List<Patch> patches = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(zfaultDeepenIn));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            patches.add(loadGeometry(line));
        }

        reader.close();
        this.patches = patches;
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

    public void loadRupSet() throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(this.rupSet);
        nameToSection = new HashMap<>();
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
                }
        );
        Set<String> ghostSections = new HashSet<>();
        patches.forEach(
                p -> {
                    List<FaultSection> sections = nameToSection.get(p.zname);

                    if ((sections == null || sections.isEmpty()) && !(p.zname.equals("Hikurangi") || (p.zname.equals("Puysegar")))) {
                        ghostSections.add(p.zname);
                    }

                    if (sections == null || sections.isEmpty()) {
                        return;
                    }
                    if (sections.size() == 1) {
                        p.section = sections.get(0);
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
                        p.section = nearest;
                    }


                }
        );

        long matches = patches.stream().filter(p -> p.section != null).count();
        System.out.println("zname matches: " + matches + " out of " + patches.size());
        long subduction = patches.stream().filter(p ->
                p.section == null && !(p.zname.equals("Hikurangi") || (p.zname.equals("Puysegar")))
        ).count();
        System.out.println("crustal without matches: " + subduction);
    }

    public void loadSolutionPolygons() throws IOException {
        FaultSystemRupSet solution = FaultSystemRupSet.load(this.solution);
        polys = solution.getModule(PolygonFaultGridAssociations.class);
    }

    public static void main(String[] args) throws IOException {
        String fileName = "C:\\rsqsimsCatalogue\\rundir5469\\zfault_Deepen.in";
        String namesFileName = "C:\\rsqsimsCatalogue\\rundir5469\\znames_Deepen.in";
        String rupSetFileName = "C:\\Users\\user\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.zip";
        String solutionFileName = "C:\\Users\\user\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NjUzOTY2Mg==.zip";
        RsqSimsLoader loader = new RsqSimsLoader(new File(fileName), new File(namesFileName), new File(rupSetFileName), new File(solutionFileName));
        loader.loadSolutionPolygons();
        List<Patch> patches = loader.loadGeometry();
        loader.loadNames();
        loader.loadRupSet();
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        patches.stream().filter(Objects::nonNull)
                .filter(p -> p.getMaxLat() < -44.2555248137888)
//                .filter(p -> p.parentId==115 || p.parentId == 3)
                .forEach(p -> builder.addFeature(p.toFeature()));
        builder.toJSON("/tmp/patches_below_dunedin.geojson");
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
}
