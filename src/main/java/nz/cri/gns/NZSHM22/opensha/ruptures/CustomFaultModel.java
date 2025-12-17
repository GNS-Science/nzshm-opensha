package nz.cri.gns.NZSHM22.opensha.ruptures;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

public class CustomFaultModel implements FileBackedModule {

    String modelData;

    public CustomFaultModel() {}

    public CustomFaultModel(String data) {
        modelData = data;
    }

    public String getModelData() {
        return modelData;
    }

    @Override
    public String getFileName() {
        return "CustomFaultModel.xml";
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
        return "CustomFaultModel";
    }
}
