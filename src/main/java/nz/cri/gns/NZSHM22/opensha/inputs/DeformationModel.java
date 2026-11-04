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
 * A deformation model, loaded from the deformationModels folder of the nzshm-input-models repo or
 * from custom data. Applies slip rates and standard deviations to the sections of a rupture set.
 */
public class DeformationModel {

    /** Location of the deformation model files in the nzshm-input-models repo. */
    static final String MODELS_PATH = "deformationModels/";

    /** Meta data key for the description. */
    public static final String META_DESCRIPTION = "description";

    /** Meta data key for the fault models or rupture sets this model is compatible with. */
    public static final String META_COMPATIBILITY = "compatibility";

    /** Meta data of the model that uses the slip rates of the fault model. */
    public static final InputModelMetaData FAULT_MODEL =
            InputModelMetaData.parse(
                    "{\"description\": \"Use deformation model as provided by the fault model\","
                            + " \"compatibility\": \"any\"}");

    /** Meta data of the model whose data is set with {@link #setCustomModel}. */
    public static final InputModelMetaData CUSTOM =
            InputModelMetaData.parse(
                    "{\"description\": \"The deformation model is specified in a file outside the"
                            + " logic tree\", \"compatibility\": \"any\"}");

    private final String fileName;

    private volatile InputModelMetaData metaData;
    private volatile Map<Integer, SlipDeformation> deformations;

    private String customModel;

    /**
     * Creates a deformation model from a file in the nzshm-input-models repo. Model properties are
     * read from the file's meta data.
     *
     * @param fileName path below the deformationModels folder
     */
    public DeformationModel(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Creates a deformation model without a file. Data can be set with {@link #setCustomModel}.
     *
     * @param metaData the model's meta data
     */
    public DeformationModel(InputModelMetaData metaData) {
        this.fileName = null;
        this.metaData = metaData;
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
     * Returns the meta data of the deformation model. Loaded from the repo once on first use.
     *
     * @return the meta data
     * @throws IllegalStateException if the deformation model file has no meta data
     */
    public InputModelMetaData getMetaData() {
        if (metaData == null) {
            synchronized (this) {
                if (metaData == null) {
                    InputModelMetaData loaded =
                            InputModel.getMetaData(InputModel.resolve(MODELS_PATH, fileName));
                    if (loaded == null) {
                        throw new IllegalStateException(
                                "No meta data in nzshm-input-models for deformation model "
                                        + fileName);
                    }
                    metaData = loaded;
                }
            }
        }
        return metaData;
    }

    /** Returns the description. */
    public String getDescription() {
        return getMetaData().getString(META_DESCRIPTION);
    }

    /** Returns the fault models or rupture sets this model is compatible with, or null. */
    public String getCompatibility() {
        return getMetaData().getString(META_COMPATIBILITY, null);
    }

    /** Returns the path below the deformationModels folder, or null if there is no file. */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the deformation model as CSV text data. Discards any previously loaded deformations.
     *
     * @param customModel the CSV data of a deformation model
     */
    public void setCustomModel(String customModel) {
        this.customModel = customModel;
        this.deformations = null;
    }

    /**
     * Sets the deformation model to the contents of the specified file.
     *
     * @param fileName the path of a deformation model CSV file
     * @throws IOException if the file cannot be read
     */
    public void setCustomModelFile(String fileName) throws IOException {
        setCustomModel(Files.readString(Paths.get(fileName)));
    }

    /** Returns the custom CSV data, or null if none has been set. */
    public String getCustomModel() {
        return customModel;
    }

    /** Returns true if this model has deformation data, either from a file or custom. */
    public boolean hasData() {
        return fileName != null || customModel != null;
    }

    /**
     * Returns a stream of the deformation model data, either the custom data or the file from the
     * nzshm-input-models repo.
     *
     * @return a stream of CSV data
     */
    public InputStream getStream() {
        if (customModel != null) {
            return new ByteArrayInputStream(customModel.getBytes(StandardCharsets.UTF_8));
        }
        InputStream in = InputModel.getStream(InputModel.resolve(MODELS_PATH, fileName));
        if (in == null) {
            throw new UncheckedIOException(
                    new FileNotFoundException(
                            "Deformation model file not found in nzshm-input-models: " + fileName));
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

    /** Loads the deformation data if there is any. Used for testing. */
    public void load() {
        if (hasData()) {
            getDeformations();
        }
    }

    /**
     * Applies the deformation model to the sections of the rupture set. Sections are matched by
     * their original id if present, otherwise by their section id. Does nothing if the model has no
     * data.
     *
     * @param rupSet the rupture set
     * @param predicate optional filter on section ids, may be null
     * @throws IllegalStateException if no section matches the deformation model
     */
    public void applyTo(FaultSystemRupSet rupSet, IntPredicate predicate) {
        if (!hasData()) {
            return;
        }
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
                        + (customModel != null ? "(custom model)" : fileName)
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
