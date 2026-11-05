package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.inputs.DeformationModel;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;

/**
 * Stores the data of deformation models that were read from local files, one per partition, so that
 * an inversion can be repeated on another machine. Models from the nzshm-input-models repo are not
 * stored. The slip rates that were applied are already stored in the rupture set's SectSlipRates.
 */
public class LocalDeformationModels implements ArchivableModule {

    static final String DIR = "local_deformation_models/";
    static final String INDEX_FILE_NAME = "index.json";

    /** One stored deformation model. */
    public static class Model {
        /** The path of the model as specified in the config. */
        public String path;

        /** The CSV data of the model. */
        public transient String data;

        /** Used by Gson. */
        protected Model() {}

        /**
         * Creates a stored model.
         *
         * @param path the path of the model as specified in the config
         * @param data the CSV data of the model
         */
        public Model(String path, String data) {
            this.path = path;
            this.data = data;
        }
    }

    protected Map<PartitionPredicate, Model> models = new LinkedHashMap<>();

    /** Used for deserialisation. */
    public LocalDeformationModels() {}

    /**
     * Collects the local deformation models of a hydrated config.
     *
     * @param config the config
     * @return the module, or null if no partition uses a local deformation model
     */
    public static LocalDeformationModels fromConfig(Config config) {
        LocalDeformationModels result = new LocalDeformationModels();
        for (PartitionConfig partition : config.partitions) {
            DeformationModel model = partition.hydratedDeformationModel;
            if (model != null && model.isLocal()) {
                result.models.put(partition.partition, new Model(model.getPath(), model.getData()));
            }
        }
        return result.models.isEmpty() ? null : result;
    }

    /** Returns the stored models by partition. */
    public Map<PartitionPredicate, Model> getModels() {
        return Collections.unmodifiableMap(models);
    }

    protected static String entryName(String entryPrefix, String fileName) {
        return ArchivableModule.getEntryName(entryPrefix, DIR + fileName);
    }

    protected static String dataFileName(PartitionPredicate partition) {
        return partition.name() + ".csv";
    }

    protected static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
        for (Map.Entry<PartitionPredicate, Model> entry : models.entrySet()) {
            output.putNextEntry(entryName(entryPrefix, dataFileName(entry.getKey())));
            output.getOutputStream().write(entry.getValue().data.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        output.putNextEntry(entryName(entryPrefix, INDEX_FILE_NAME));
        Writer writer = new OutputStreamWriter(output.getOutputStream(), StandardCharsets.UTF_8);
        gson().toJson(models, writer);
        writer.flush();
        output.closeEntry();
    }

    @Override
    public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
        Map<PartitionPredicate, Model> index;
        try (InputStream in = input.getInputStream(entryName(entryPrefix, INDEX_FILE_NAME))) {
            index =
                    gson().fromJson(
                                    new InputStreamReader(in, StandardCharsets.UTF_8),
                                    new TypeToken<
                                            LinkedHashMap<
                                                    PartitionPredicate, Model>>() {}.getType());
        }
        for (Map.Entry<PartitionPredicate, Model> entry : index.entrySet()) {
            try (InputStream in =
                    input.getInputStream(entryName(entryPrefix, dataFileName(entry.getKey())))) {
                entry.getValue().data = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        models = index;
    }

    @Override
    public String getName() {
        return "Local Deformation Models";
    }
}
