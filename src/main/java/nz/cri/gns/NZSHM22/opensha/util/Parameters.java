package nz.cri.gns.NZSHM22.opensha.util;

import java.io.*;
import java.util.HashMap;

/**
 * Helper class that can parse in parameters that are copied and pasted from a toshi webpage.
 * Can be used to reproduce runs.
 */

public class Parameters extends HashMap<String, String> {

    private final static String RESOURCE_PATH = "/parameters/";

    /**
     * NZSHM22 parameters that can be used to reproduce NZSHM22 results.
     */
    public enum NZSHM22 {
        INVERSION_CRUSTAL("SW52ZXJzaW9uU29sdXRpb246NjMzMzY3Mw==.txt"),
        INVERSION_HIKURANGI("SW52ZXJzaW9uU29sdXRpb246MTEzMTc0.txt"),
        INVERSION_PUYSEGUR("SW52ZXJzaW9uU29sdXRpb246MTI5MTAyNQ==.txt"),
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

    /**
     * Returns a parameters object with the values from the InputStream.
     * This expects a text stream with parameters divided by newlines.
     * Keys and values are separated by a tab character.
     * Lines beginning with a semicolon are ignored.
     * Lines not separating exactly two strings with a tab are ignored.
     *
     * @param in the InputStream
     * @return the Parameters object
     * @throws IOException
     */
    public static Parameters fromInputStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        Parameters arguments = new Parameters();
        reader.lines().forEach(line -> {
            if (!line.startsWith(";")) {
                String[] kp = line.split("[\t]");
                if (kp.length == 2) {
                    arguments.put(kp[0].trim(), kp[1].trim());
                }
            }
        });
        reader.close();
        return arguments;
    }

    /**
     * Convenience function to get a double value
     *
     * @param key the key
     * @return a double value associated with the key
     */
    public double getDouble(String key) {
        String value = get(key);
        return Double.parseDouble(value);
    }

    /**
     * Convenience function to get a double value
     *
     * @param key          the key
     * @param defaultValue the value to return if the parameter does not exist.
     * @return a double value associated with the key
     */
    public double getDouble(String key, double defaultValue) {
        if (get(key) == null) {
            return defaultValue;
        } else {
            return getDouble(key);
        }
    }

    /**
     * Convenience function to get a float value
     *
     * @param key the key
     * @return a float value associated with the key
     */
    public float getFloat(String key) {
        String value = get(key);
        return Float.parseFloat(value);
    }

    /**
     * Convenience function to get an integer value
     *
     * @param key the key
     * @return a integer value associated with the key
     */
    public int getInteger(String key) {
        String value = get(key);
        return Integer.parseInt(value);
    }

    /**
     * Convenience function to get a long value
     *
     * @param key the key
     * @return a long value associated with the key
     */
    public long getLong(String key) {
        String value = get(key);
        return Long.parseLong(value);
    }

    /**
     * Convenience function to get a boolean value
     *
     * @param key the key
     * @return a boolean value associated with the key
     */
    public boolean getBoolean(String key) {
        return get(key) != null && get(key).equalsIgnoreCase("true");
    }

    /**
     * Convenience function to check if a value is set and no zero - mimicking Python's interpretation of truthiness.
     * @param key
     * @return
     */
    public boolean isNotZero(String key) {
        return containsKey(key) && Double.parseDouble(get(key)) != 0;
    }
}

