package nz.cri.gns.NZSHM22.opensha.ruptures;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

/**
 * This module gets added to a solution when the user has specified a CUSTOM deformation model in a
 * file. It gets injected into the logic tree's deformation model in
 * NZSHM22_InversionFaultSystemRuptSet.init() and in RuptureSetSetup.setup().
 *
 * <p>The archive file name is fixed, so an archive can only carry one custom deformation model.
 */
public class CustomDeformationModel implements FileBackedModule {

    String modelData;

    // default constructor for deserialisation
    public CustomDeformationModel() {}

    public CustomDeformationModel(String data) {
        modelData = data;
    }

    public String getModelData() {
        return modelData;
    }

    @Override
    public String getFileName() {
        return "CustomDeformationModel.csv";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out);
        writer.write(modelData);
        writer.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        modelData = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String getName() {
        return "CustomDeformationModel";
    }
}
