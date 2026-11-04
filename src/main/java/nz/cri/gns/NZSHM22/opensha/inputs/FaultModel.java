package nz.cri.gns.NZSHM22.opensha.inputs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;

/**
 * A fault model, loaded from the faultModels folder of the nzshm-input-models repo or from custom
 * data.
 */
public class FaultModel {

    /** Location of the fault model files in the nzshm-input-models repo. */
    static final String MODELS_PATH = "faultModels/";

    /** Meta data key for the model name. */
    public static final String META_MODEL_NAME = "modelName";

    /** Meta data key for whether the model is crustal. */
    public static final String META_CRUSTAL = "crustal";

    /** Meta data key for the TVZ domain. Optional. */
    public static final String META_TVZ_DOMAIN = "tvzDomain";

    /** Meta data key for the parent section id of subduction models. */
    public static final String META_PARENT_SECTION_ID = "parentSectionId";

    /** Meta data key for the {@link PartitionPredicate} name. */
    public static final String META_PARTITION = "partition";

    /** Meta data of the custom fault model, used when no fault model file is given. */
    public static final String CUSTOM_META_DATA =
            "{\"modelName\": \"The fault model is specified in a file outside the logic tree\","
                    + " \"crustal\": true, \"tvzDomain\": \"4\", \"partition\": \"CRUSTAL\"}";

    private final String fileName;

    private volatile InputModelMetaData metaData;
    private volatile Map<String, List<Integer>> namedFaultsMapAlt;

    private String customModel;
    private String customNamedFaults;

    /**
     * Creates a fault model from a file in the nzshm-input-models repo. Model properties are read
     * from the file's meta data.
     *
     * @param fileName path below the faultModels folder
     */
    public FaultModel(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Creates a custom fault model without a file. Set its data with {@link #setCustomModel}.
     *
     * @param metaData the model's meta data
     */
    public FaultModel(InputModelMetaData metaData) {
        this.fileName = null;
        this.metaData = metaData;
    }

    /** Sets the custom model as text data (XML for crustal, CSV for subduction). */
    public void setCustomModel(String customModel) {
        this.customModel = customModel;
    }

    public String getCustomModel() {
        return customModel;
    }

    public void setCustomNamedFaults(String customNamedFaults) {
        this.customNamedFaults = customNamedFaults;
    }

    public String getCustomNamedFaults() {
        return customNamedFaults;
    }

    /**
     * Loads the sections of the fault model into sections.
     *
     * @param sections a list to be populated by fault sections.
     */
    public void fetchFaultSections(FaultSectionList sections)
            throws IOException, DocumentException {
        if (customModel != null) {
            try (InputStream in = new ByteArrayInputStream(customModel.getBytes())) {
                fetchFaultSections(
                        sections,
                        in,
                        isCrustal(),
                        getParentSectionId(),
                        getModelName(),
                        getPartition());
            }
        }
        if (fileName != null) {
            fetchFaultSections(
                    sections,
                    fileName,
                    isCrustal(),
                    getParentSectionId(),
                    getModelName(),
                    getPartition());
        }
    }

    /**
     * Returns a mapping from named fault to the ids of the sections in that fault.
     *
     * @return the map from fault name to section ids, or null if there is no named faults data
     */
    public Map<String, List<Integer>> getNamedFaultsMapAlt() {
        if (namedFaultsMapAlt == null) {
            synchronized (this) {
                if (namedFaultsMapAlt == null) {
                    InputStream in = null;
                    if (customNamedFaults != null) {
                        in = new ByteArrayInputStream(customNamedFaults.getBytes());
                    } else if (fileName != null) {
                        in = getNamedFaultsStream(fileName);
                    }
                    if (in != null) {
                        try {
                            namedFaultsMapAlt = parseNamedFaults(in);
                        } catch (Throwable t) {
                            throw ExceptionUtils.asRuntimeException(t);
                        }
                    }
                }
            }
        }
        return namedFaultsMapAlt;
    }

    /**
     * Returns the meta data of the fault model. Loaded from the repo once on first use.
     *
     * @return the meta data
     * @throws IllegalStateException if the fault model file has no meta data
     */
    public InputModelMetaData getMetaData() {
        if (metaData == null) {
            synchronized (this) {
                if (metaData == null) {
                    InputModelMetaData loaded = InputModel.getMetaData(MODELS_PATH + fileName);
                    if (loaded == null) {
                        throw new IllegalStateException(
                                "No meta data in nzshm-input-models for fault model " + fileName);
                    }
                    metaData = loaded;
                }
            }
        }
        return metaData;
    }

    /** Returns the model name. */
    public String getModelName() {
        return getMetaData().getString(META_MODEL_NAME);
    }

    /** Returns the path below the faultModels folder, or null for a custom model. */
    public String getFileName() {
        return fileName;
    }

    /** Returns true if this is a crustal model. */
    public boolean isCrustal() {
        return getMetaData().getBoolean(META_CRUSTAL);
    }

    /** Returns the parent section id of a subduction model, or -1 if not set. */
    public int getParentSectionId() {
        return getMetaData().getInt(META_PARENT_SECTION_ID, -1);
    }

    /** Returns the TVZ domain, or null if not set. */
    public String getTvzDomain() {
        return getMetaData().getString(META_TVZ_DOMAIN, null);
    }

    /** Returns the partition of the model's sections. */
    public PartitionPredicate getPartition() {
        return PartitionPredicate.valueOf(getMetaData().getString(META_PARTITION));
    }

    /**
     * Opens a fault model file from the main branch of the nzshm-input-models repo.
     *
     * @param fileName path below the faultModels folder
     * @return the file's content, or null if the file does not exist
     */
    public static InputStream getStream(String fileName) {
        return InputModel.getStream(MODELS_PATH + fileName);
    }

    /**
     * Loads the sections of a fault model file into sections.
     *
     * @param fileName path below the faultModels folder
     * @throws FileNotFoundException if the file does not exist
     */
    public static void fetchFaultSections(
            FaultSectionList sections,
            String fileName,
            boolean crustal,
            int subductionId,
            String modelName,
            PartitionPredicate partition)
            throws IOException, DocumentException {
        try (InputStream in = getStream(fileName)) {
            if (in == null) {
                throw new FileNotFoundException(
                        "Fault model file not found in nzshm-input-models: " + fileName);
            }
            fetchFaultSections(sections, in, crustal, subductionId, modelName, partition);
        }
    }

    /** Loads fault sections from in: XML for crustal models, CSV for subduction models. */
    public static void fetchFaultSections(
            FaultSectionList sections,
            InputStream in,
            boolean crustal,
            int subductionId,
            String modelName,
            PartitionPredicate partition)
            throws IOException, DocumentException {
        if (crustal) {
            loadStoredFaultSections(sections, XMLUtils.loadDocument(in));
        } else {
            DownDipSubSectBuilder.loadFromStream(sections, subductionId, modelName, in, partition);
        }
    }

    public static void loadStoredFaultSections(FaultSectionList sections, Document doc) {
        Element el = doc.getRootElement().element("FaultModel");
        for (int i = 0; i < el.elements().size(); i++) {
            Element subEl = el.element("i" + i);
            sections.add(NZFaultSection.fromXMLMetadata(subEl));
        }
    }

    /**
     * Opens the named faults file that belongs to a fault model file.
     *
     * @param fileName the fault model's path below the faultModels folder
     * @return the file's content, or null if the file does not exist
     */
    public static InputStream getNamedFaultsStream(String fileName) {
        return getStream(fileName + ".FaultsByNameAlt.txt");
    }

    /** Parses a named faults file into a map from fault name to section ids. */
    public static Map<String, List<Integer>> parseNamedFaults(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in)) {
            return scratch.UCERF3.enumTreeBranches.FaultModels.parseNamedFaultsAltFile(reader);
        }
    }
}
