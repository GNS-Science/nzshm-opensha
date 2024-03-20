package nz.cri.gns.NZSHM22.opensha.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public enum NZSHM22_Parameters {

    INVERSION_CRUSTAL("SW52ZXJzaW9uU29sdXRpb246NjMzMzY3Mw==.txt");

    private final static String RESOURCE_PATH = "/parameters/";
    private final String fileName;

    NZSHM22_Parameters(String fileName) {
        this.fileName = fileName;
    }

    public InputStream getStream(String fileName) {
        return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
    }


    public PythonGatewayJsonRunner.MapWithPrimitives getParameters() throws IOException {
        try (InputStream in = getStream(fileName)) {
            return PythonGatewayJsonRunner.readTableArguments(in);
        }
    }

}
