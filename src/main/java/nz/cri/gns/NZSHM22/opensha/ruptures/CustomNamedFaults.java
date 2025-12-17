package nz.cri.gns.NZSHM22.opensha.ruptures;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

/**
 * This module gets added to a rupture set when the user has specified custom named fault names. It
 * gets injected into the logic tree's fault model in NZSHM22_InversionFaultSystemRuptSet.init()
 */
public class CustomNamedFaults implements FileBackedModule {

    String namedFaults;

    // default constructor for deserialisation
    public CustomNamedFaults() {}

    public CustomNamedFaults(String namedFaults) {
        this.namedFaults = namedFaults;
    }

    public String getNamedFaults() {
        return namedFaults;
    }

    @Override
    public String getFileName() {
        return "CustomNamedFaults.txt";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out);
        writer.write(namedFaults);
        writer.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        namedFaults = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String getName() {
        return "Custom Named Faults";
    }
}
