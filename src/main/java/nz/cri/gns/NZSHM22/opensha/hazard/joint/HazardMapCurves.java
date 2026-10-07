package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

/**
 * Hazard map curves of a solution, carried as a solution module so that maps can be rebuilt without
 * recalculating the hazard.
 *
 * <p>The module holds any number of curve sets, one per {@link Key}: GMM mode, background option,
 * period, the rupture rates the calculation saw, map nodes and intensity levels.
 *
 * <p>Curves are only ever attached explicitly, with {@link #addTo} or {@link
 * JointHazardMapCalculator#attachCurves}; writing the solution then stores them. Using them is
 * transparent: {@link JointHazardMapCalculator#calcHazardCurves()} takes whatever curves the module
 * of its solution has for its inputs and calculates only the rest.
 *
 * <p>Follows OpenSHA's precomputed module pattern: this class is the module type a solution maps
 * to, {@link Precomputed} is the archivable implementation.
 */
public abstract class HazardMapCurves implements OpenSHA_Module {

    /**
     * Version of the curve calculation. Curves written under another version are ignored when the
     * module is loaded. Bump it when a code change alters the curves without changing the {@link
     * Key}, e.g. GMM setup, site parameters, intensity levels or solution preprocessing.
     */
    public static final int CURVES_VERSION = 1;

    /**
     * Identifies a set of curves: everything that changes the curves at a given node, plus the
     * nodes themselves.
     */
    public static class Key {
        public final JointHazardInput.GmmMode gmmMode;
        public final IncludeBackgroundOption background;
        public final double period;

        /** Hash of the rupture rates the calculation used. See {@link #ratesHash}. */
        public final String ratesHash;

        /** Hash of the map node locations. See {@link #regionHash}. */
        public final String regionHash;

        /** Hash of the intensity measure levels. See {@link #xValsHash}. */
        public final String xValsHash;

        public Key(
                JointHazardInput.GmmMode gmmMode,
                IncludeBackgroundOption background,
                double period,
                String ratesHash,
                String regionHash,
                String xValsHash) {
            this.gmmMode = gmmMode;
            this.background = background;
            this.period = period;
            this.ratesHash = ratesHash;
            this.regionHash = regionHash;
            this.xValsHash = xValsHash;
        }

        /** The key of the curves that the given inputs produce for a period. */
        public static Key of(JointHazardInput input, double period) {
            return keys(input, period).get(0);
        }

        /**
         * The keys of the curves that the given inputs produce for each period, in the same order.
         * Cheaper than calling {@link #of} per period, as the hashes are only built once.
         */
        public static List<Key> keys(JointHazardInput input, double... periods) {
            return keys(input, input.getSolution(), periods);
        }

        /**
         * The keys of the curves calculated for a solution with the settings of the given inputs,
         * for each period. Lets a part of a {@link JointHazardInput#combined} input be keyed on its
         * own.
         */
        public static List<Key> keys(
                JointHazardInput input, FaultSystemSolution solution, double... periods) {
            String ratesHash = ratesHash(solution);
            String regionHash = regionHash(input.getRegion());
            String xValsHash = xValsHash(JointHazardMapCalculator.mapXVals());
            List<Key> keys = new ArrayList<>();
            for (double period : periods) {
                keys.add(
                        new Key(
                                input.getGmmMode(),
                                JointHazardMapCalculator.BACKGROUND,
                                period,
                                ratesHash,
                                regionHash,
                                xValsHash));
            }
            return keys;
        }

        /** A stable, file name safe id. */
        public String id() {
            return sha256(toString()).substring(0, 16);
        }

        @Override
        public String toString() {
            return gmmMode
                    + "|"
                    + background
                    + "|"
                    + (float) period
                    + "|"
                    + ratesHash
                    + "|"
                    + regionHash
                    + "|"
                    + xValsHash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && toString().equals(o.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

    @Override
    public String getName() {
        return "Hazard Map Curves";
    }

    /** The keys of all curve sets in the module. */
    public abstract List<Key> getKeys();

    /**
     * The curves for a key.
     *
     * @param region the region the curves were calculated over, used to check the node locations
     * @return one curve per region node, or null if the module has none for the key
     */
    public abstract DiscretizedFunc[] getCurves(Key key, GriddedRegion region);

    public boolean contains(Key key) {
        return getKeys().contains(key);
    }

    /**
     * Attaches curves to a solution: replaces its module with one that holds its current curves
     * plus the given ones. Write the solution to keep them.
     *
     * @param solution the solution to attach the curves to, which they are keyed on. The curves
     *     must have been calculated for its ruptures and rates: the inputs' solution, or a part of
     *     a {@link JointHazardInput#combined} input.
     * @param input the inputs the curves were calculated with
     * @param curves one array of curves per period of the inputs, in the same order, each indexed
     *     by region node
     * @return the solution's new module
     */
    public static HazardMapCurves addTo(
            FaultSystemSolution solution, JointHazardInput input, List<DiscretizedFunc[]> curves) {
        Preconditions.checkArgument(
                curves.size() == input.getPeriods().length,
                "Need curves for %s periods, got %s",
                input.getPeriods().length,
                curves.size());
        List<Key> keys = Key.keys(input, solution, input.getPeriods());
        // the module container locks on itself, so this keeps concurrent additions from losing
        // each other's curves
        synchronized (solution) {
            HazardMapCurves existing = solution.getModule(HazardMapCurves.class);
            Precomputed module =
                    existing instanceof Precomputed ? (Precomputed) existing : new Precomputed();
            for (int i = 0; i < keys.size(); i++) {
                module = module.with(keys.get(i), input.getRegion(), curves.get(i));
            }
            solution.addModule(module);
            return module;
        }
    }

    /** The archivable implementation: curves kept as CSVs in the solution archive. Immutable. */
    public static class Precomputed extends HazardMapCurves implements ArchivableModule {

        /** Directory of the module within the archive. */
        public static final String DIR = "hazard_map_curves/";

        public static final String INDEX_FILE_NAME = "index.json";

        /** One curve set as recorded in the index. */
        protected static class Record {
            Key key;
            String file;
        }

        /** The contents of {@link #INDEX_FILE_NAME}. */
        protected static class Index {
            int version;
            List<Record> curves = new ArrayList<>();
        }

        /** Curve CSVs by key, as written to the archive. */
        protected Map<Key, byte[]> curves;

        /** An empty module; also used when loading from an archive. */
        protected Precomputed() {
            this(Collections.emptyMap());
        }

        protected Precomputed(Map<Key, byte[]> curves) {
            this.curves = Collections.unmodifiableMap(new LinkedHashMap<>(curves));
        }

        /**
         * A module holding this module's curves plus the given ones, which replace any under the
         * same key.
         *
         * @param curves one curve per region node
         */
        public Precomputed with(Key key, GriddedRegion region, DiscretizedFunc[] curves) {
            Preconditions.checkArgument(
                    curves.length == region.getNodeCount(),
                    "Need %s curves, got %s",
                    region.getNodeCount(),
                    curves.length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                SolHazardMapCalc.buildCurvesCSV(curves, region.getNodeList()).writeToStream(out);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Map<Key, byte[]> extended = new LinkedHashMap<>(this.curves);
            extended.put(key, out.toByteArray());
            return new Precomputed(extended);
        }

        @Override
        public List<Key> getKeys() {
            return new ArrayList<>(curves.keySet());
        }

        @Override
        public boolean contains(Key key) {
            return curves.containsKey(key);
        }

        @Override
        public DiscretizedFunc[] getCurves(Key key, GriddedRegion region) {
            byte[] csvBytes = curves.get(key);
            if (csvBytes == null) {
                return null;
            }
            try {
                CSVFile<String> csv = CSVFile.readStream(new ByteArrayInputStream(csvBytes), true);
                return SolHazardMapCalc.loadCurvesCSV(csv, region);
            } catch (IOException | RuntimeException e) {
                System.err.println("Ignoring unreadable hazard map curves " + key + ": " + e);
                return null;
            }
        }

        @Override
        public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
            Index index = new Index();
            index.version = CURVES_VERSION;
            for (Map.Entry<Key, byte[]> entry : curves.entrySet()) {
                Record record = new Record();
                record.key = entry.getKey();
                record.file = entry.getKey().id() + ".csv";
                index.curves.add(record);
                output.putNextEntry(entryName(entryPrefix, record.file));
                output.getOutputStream().write(entry.getValue());
                output.closeEntry();
            }
            output.putNextEntry(entryName(entryPrefix, INDEX_FILE_NAME));
            OutputStream out = output.getOutputStream();
            out.write(gson().toJson(index).getBytes(StandardCharsets.UTF_8));
            out.flush();
            output.closeEntry();
        }

        /**
         * Loads the curves. Curves written under another {@link #CURVES_VERSION} are reported and
         * dropped, leaving the module empty.
         */
        @Override
        public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
            Index index;
            try (InputStream in = input.getInputStream(entryName(entryPrefix, INDEX_FILE_NAME))) {
                index =
                        gson().fromJson(
                                        new InputStreamReader(in, StandardCharsets.UTF_8),
                                        Index.class);
            }
            if (index.version != CURVES_VERSION) {
                System.out.println(
                        "Ignoring hazard map curves written by version "
                                + index.version
                                + ", current version is "
                                + CURVES_VERSION);
                curves = Collections.emptyMap();
                return;
            }
            Map<Key, byte[]> loaded = new LinkedHashMap<>();
            for (Record record : index.curves) {
                try (InputStream in = input.getInputStream(entryName(entryPrefix, record.file))) {
                    loaded.put(record.key, in.readAllBytes());
                }
            }
            curves = Collections.unmodifiableMap(loaded);
        }

        protected static String entryName(String entryPrefix, String fileName) {
            return ArchivableModule.getEntryName(entryPrefix, DIR + fileName);
        }
    }

    /** A hash of a solution's rupture rates. */
    public static String ratesHash(FaultSystemSolution solution) {
        double[] rates = solution.getRateForAllRups();
        ByteBuffer buffer = ByteBuffer.allocate(rates.length * Double.BYTES);
        for (double rate : rates) {
            buffer.putDouble(rate);
        }
        return hex(digest().digest(buffer.array())).substring(0, 16);
    }

    /** A hash of a region's node locations, rounded to well below any grid spacing. */
    public static String regionHash(GriddedRegion region) {
        StringBuilder builder = new StringBuilder();
        for (Location location : region.getNodeList()) {
            builder.append(String.format("%.5f,%.5f;", location.lat, location.lon));
        }
        return sha256(builder.toString()).substring(0, 16);
    }

    /** A hash of the x values of a function, compared as floats. */
    public static String xValsHash(DiscretizedFunc xVals) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < xVals.size(); i++) {
            builder.append((float) xVals.getX(i)).append(';');
        }
        return sha256(builder.toString()).substring(0, 16);
    }

    protected static String sha256(String value) {
        return hex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    protected static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    protected static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
