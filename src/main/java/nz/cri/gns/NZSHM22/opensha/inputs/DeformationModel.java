package nz.cri.gns.NZSHM22.opensha.inputs;

import com.google.common.base.Preconditions;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * A deformation model, loaded from the deformationModels folder of the nzshm-input-models repo,
 * from a local file (path prefixed with {@value InputModel#FILE_PREFIX}), or from CSV data. Applies
 * slip rates and standard deviations to the sections of a rupture set.
 */
public class DeformationModel {

    /** Location of the deformation model files in the nzshm-input-models repo. */
    static final String MODELS_PATH = "deformationModels/";

    /** Meta data key for the description. */
    public static final String META_DESCRIPTION = "description";

    /** Meta data key for the fault models or rupture sets this model is compatible with. */
    public static final String META_COMPATIBILITY = "compatibility";

    /** Meta data of models that have none, such as local files without a meta data file. */
    static final InputModelMetaData NO_META_DATA = InputModelMetaData.parse("{}");

    private final String path;

    private volatile InputModelMetaData metaData;
    private volatile Map<Integer, SlipDeformation> deformations;
    private volatile String data;

    /**
     * Creates a deformation model from a file. Model properties are read from the file's meta data.
     *
     * @param path path below the deformationModels folder of the nzshm-input-models repo, or a
     *     local file prefixed with {@value InputModel#FILE_PREFIX}
     */
    public DeformationModel(String path) {
        this.path = path;
    }

    /**
     * Creates a deformation model from CSV data. The model has no meta data.
     *
     * @param data the CSV data of a deformation model
     * @return the deformation model
     */
    public static DeformationModel fromData(String data) {
        DeformationModel model = new DeformationModel((String) null);
        model.data = data;
        model.metaData = NO_META_DATA;
        return model;
    }

    /** One row of a deformation model file. */
    public static class SlipDeformation {
        public int sectionId;
        public int parentId;
        public double slip;
        public double stdv;

        /** The 1-based line number of this row in the deformation model file. */
        public int rowNum;
    }

    /**
     * Returns the meta data of the deformation model. Loaded once on first use. Meta data is
     * optional for local files.
     *
     * @return the meta data
     * @throws IllegalStateException if a deformation model file in the repo has no meta data
     */
    public InputModelMetaData getMetaData() {
        if (metaData == null) {
            synchronized (this) {
                if (metaData == null) {
                    InputModelMetaData loaded =
                            InputModel.getMetaData(InputModel.resolve(MODELS_PATH, path));
                    if (loaded == null) {
                        if (!isLocal()) {
                            throw new IllegalStateException(
                                    "No meta data in nzshm-input-models for deformation model "
                                            + path);
                        }
                        loaded = NO_META_DATA;
                    }
                    metaData = loaded;
                }
            }
        }
        return metaData;
    }

    /** Returns the description, or null if there is none. */
    public String getDescription() {
        return getMetaData().getString(META_DESCRIPTION, null);
    }

    /** Returns the fault models or rupture sets this model is compatible with, or null. */
    public String getCompatibility() {
        return getMetaData().getString(META_COMPATIBILITY, null);
    }

    /** Returns the path of the model's file, or null if the model was created from data. */
    public String getPath() {
        return path;
    }

    /** Returns true if the model is read from a local file rather than from the repo. */
    public boolean isLocal() {
        return path != null && path.startsWith(InputModel.FILE_PREFIX);
    }

    /**
     * Returns the CSV data of the deformation model. Read once on first use.
     *
     * @return the CSV data
     */
    public String getData() {
        if (data == null) {
            synchronized (this) {
                if (data == null) {
                    try (InputStream in = openFile()) {
                        data = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }
                }
            }
        }
        return data;
    }

    /**
     * Returns a stream of the deformation model's CSV data.
     *
     * @return a stream of CSV data
     */
    public InputStream getStream() {
        return new ByteArrayInputStream(getData().getBytes(StandardCharsets.UTF_8));
    }

    /** Opens the model's file in the repo or on the local file system. */
    protected InputStream openFile() {
        InputStream in = InputModel.getStream(InputModel.resolve(MODELS_PATH, path));
        if (in == null) {
            throw new UncheckedIOException(
                    new FileNotFoundException("Deformation model file not found: " + path));
        }
        return in;
    }

    /**
     * Parses deformation model CSV data. Lines starting with "%" or "//" are comments.
     *
     * @param deformationsFile the CSV data
     * @return a map from section id to deformation
     */
    public static Map<Integer, SlipDeformation> loadDeformations(InputStream deformationsFile)
            throws IOException {
        Map<Integer, SlipDeformation> result = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(deformationsFile))) {
            String line;
            // 1-based so that it matches the line numbers of a text editor
            int rowNum = 0;
            while ((line = in.readLine()) != null) {
                // comments are counted so that row numbers reflect the actual file position
                rowNum++;
                if (line.startsWith("%") || line.startsWith("//")) {
                    continue;
                }
                try {
                    String[] data = line.split(",");
                    SlipDeformation deformation = new SlipDeformation();
                    deformation.sectionId = Integer.parseInt(data[0].trim());
                    deformation.parentId = Integer.parseInt(data[1].trim());
                    deformation.slip = Double.parseDouble(data[2].trim());
                    deformation.stdv = Double.parseDouble(data[3].trim());
                    deformation.rowNum = rowNum;
                    result.put(deformation.sectionId, deformation);
                } catch (Exception x) {
                    throw new RuntimeException(
                            "Error parsing deformation model at line " + rowNum, x);
                }
            }
            return result;
        }
    }

    /** Returns the parsed deformations. Loaded once on first use. */
    protected Map<Integer, SlipDeformation> getDeformations() {
        if (deformations == null) {
            synchronized (this) {
                if (deformations == null) {
                    try {
                        deformations = loadDeformations(getStream());
                    } catch (IOException x) {
                        throw new UncheckedIOException(x);
                    }
                }
            }
        }
        return deformations;
    }

    /** Loads and parses the deformation data. Used for testing. */
    public void load() {
        getDeformations();
    }

    /**
     * Applies the deformation model to the sections of the rupture set. Sections are matched by
     * their original id if present, otherwise by their section id.
     *
     * @param rupSet the rupture set
     * @param predicate optional filter on section ids, may be null
     * @throws IllegalStateException if no section matches the deformation model
     */
    public void applyTo(FaultSystemRupSet rupSet, IntPredicate predicate) {
        int matches = 0;
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            if (predicate != null && !predicate.test(section.getSectionId())) {
                continue;
            }

            FaultSectionProperties props = new FaultSectionProperties(section);
            int sectionId = section.getSectionId();
            if (props.getOriginalId() != null) {
                sectionId = props.getOriginalId();
            }
            int parentId = section.getParentSectionId();
            if (props.getOriginalParent() != null) {
                parentId = props.getOriginalParent();
            }

            SlipDeformation deformation = getDeformations().get(sectionId);
            if (deformation != null) {
                Preconditions.checkArgument(
                        deformation.sectionId == sectionId,
                        "Deformation section id does not match section id."
                                + " (deformation model line "
                                + deformation.rowNum
                                + ")");
                Preconditions.checkArgument(
                        deformation.parentId == parentId,
                        "Section "
                                + section.getSectionId()
                                + " Deformation parent id "
                                + deformation.parentId
                                + " does not match section parent id "
                                + parentId
                                + " (deformation model line "
                                + deformation.rowNum
                                + ")");
                section.setAveSlipRate(deformation.slip);
                section.setSlipRateStdDev(deformation.stdv);
                matches++;
            }
        }
        Preconditions.checkState(
                matches > 0,
                "Deformation model "
                        + (path == null ? "(custom model)" : path)
                        + " does not match any sections in the rupture set.");
    }

    /**
     * Transforms a subduction fault model into a deformation model file, written to the current
     * directory with a "dm_x" prefix.
     *
     * @param subductionFaultModelFile the subduction fault model CSV file
     * @param partition the partition of the fault model
     */
    public static void subductionFmToDm(
            Path subductionFaultModelFile, PartitionPredicate partition) {
        try {
            FaultSectionList sections = new FaultSectionList();
            InputStream in = new FileInputStream(subductionFaultModelFile.toFile());
            FaultModel.fetchFaultSections(sections, in, false, 10000, "", partition);

            try (PrintWriter out =
                    new PrintWriter(
                            new FileWriter(
                                    "dm_x" + subductionFaultModelFile.getFileName().toString()))) {
                out.println("% generated from faultmodel file " + subductionFaultModelFile);
                for (FaultSection section : sections) {
                    out.println(
                            ""
                                    + section.getSectionId()
                                    + ", "
                                    + section.getParentSectionId()
                                    + ", "
                                    + section.getOrigAveSlipRate()
                                    + ", "
                                    + section.getOrigSlipRateStdDev());
                }

            } catch (IOException x) {
                x.printStackTrace();
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    /** Converts all subduction fault models in C:\tmp\temp\ to deformation models. */
    public static void main(String[] args) throws DocumentException, IOException {
        PartitionPredicate partition = PartitionPredicate.HIKURANGI;
        Files.list(Paths.get("C:\\tmp\\temp\\")).forEach(path -> subductionFmToDm(path, partition));
    }
}
