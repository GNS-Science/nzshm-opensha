package nz.cri.gns.NZSHM22.opensha.util;

import java.io.*;
import java.util.HashMap;

public class Parameters extends HashMap<String, String> {

    private final static String RESOURCE_PATH = "/parameters/";

    public enum NZSHM22 {
        INVERSION_CRUSTAL("SW52ZXJzaW9uU29sdXRpb246NjMzMzY3Mw==.txt"),
        RUPSET_CRUSTAL("RmlsZToxMDAwODc=.txt"),
        RUPSET_HIKURANGI("RmlsZTo3MTQ3LjVramh3Rg==.txt"),
        RUPSET_PUYSEGUR("RmlsZToxMjkwOTg0.txt");

        private final String fileName;

        NZSHM22(String fileName) {
            this.fileName = fileName;
        }

        public Parameters getParameters() throws IOException {
            try (InputStream in = getStream(fileName)) {
                return fromInputStream(in);
            }
        }

        InputStream getStream(String fileName) {
            return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
        }

    }

    public static Parameters fromFile(File file) throws IOException {
        return fromInputStream(new FileInputStream(file));
    }

    public static Parameters fromInputStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        Parameters arguments = new Parameters();
        reader.lines().forEach(line -> {
            if (!line.startsWith(";")) {
                String[] kp = line.split("\t");
                if (kp.length == 2) {
                    arguments.put(kp[0].trim(), kp[1].trim());
                }
            }
        });
        reader.close();
        return arguments;
    }

    public double getDouble(String key) {
        String value = get(key);
        return Double.parseDouble(value);
    }

    public double getDouble(String key, double defaultValue) {
        if (get(key) == null) {
            return defaultValue;
        } else {
            return getDouble(key);
        }
    }

    public float getFloat(String key) {
        String value = get(key);
        return Float.parseFloat(value);
    }

    public int getInteger(String key) {
        String value = get(key);
        return Integer.parseInt(value);
    }

    public long getLong(String key) {
        String value = get(key);
        return Long.parseLong(value);
    }

    public boolean getBoolean(String key) {
        return get(key) != null && get(key).equalsIgnoreCase("true");
    }
}

