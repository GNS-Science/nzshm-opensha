package nz.cri.gns.NZSHM22.opensha.inputs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Meta data of an input model file, stored next to the file in the nzshm-input-models repo as a
 * JSON dictionary.
 */
public class InputModelMetaData {

    protected final JsonObject json;

    /**
     * Creates meta data from a JSON dictionary.
     *
     * @param json the meta data
     */
    public InputModelMetaData(JsonObject json) {
        this.json = json;
    }

    /**
     * Parses meta data from a JSON string.
     *
     * @param json a JSON dictionary
     * @return the meta data
     */
    public static InputModelMetaData parse(String json) {
        return new InputModelMetaData(JsonParser.parseString(json).getAsJsonObject());
    }

    /**
     * Parses meta data from a JSON dictionary. Closes the stream.
     *
     * @param in the JSON content
     * @return the meta data
     */
    public static InputModelMetaData parse(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new InputModelMetaData(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /** Returns true if key exists and is not null. */
    public boolean has(String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    /** Returns the value of key, or null if it does not exist. */
    public JsonElement get(String key) {
        return has(key) ? json.get(key) : null;
    }

    /**
     * Returns the value of key.
     *
     * @throws IllegalStateException if key does not exist
     */
    public JsonElement require(String key) {
        if (!has(key)) {
            throw new IllegalStateException("Meta data is missing required key " + key);
        }
        return json.get(key);
    }

    /**
     * Returns the value of key as a string.
     *
     * @throws IllegalStateException if key does not exist
     */
    public String getString(String key) {
        return require(key).getAsString();
    }

    /**
     * Returns the value of key as a boolean.
     *
     * @throws IllegalStateException if key does not exist
     */
    public boolean getBoolean(String key) {
        return require(key).getAsBoolean();
    }

    /**
     * Returns the value of key as an int.
     *
     * @throws IllegalStateException if key does not exist
     */
    public int getInt(String key) {
        return require(key).getAsInt();
    }

    /** Returns the value of key as a string, or defaultValue if it does not exist. */
    public String getString(String key, String defaultValue) {
        return has(key) ? json.get(key).getAsString() : defaultValue;
    }

    /** Returns the value of key as a boolean, or defaultValue if it does not exist. */
    public boolean getBoolean(String key, boolean defaultValue) {
        return has(key) ? json.get(key).getAsBoolean() : defaultValue;
    }

    /** Returns the value of key as an int, or defaultValue if it does not exist. */
    public int getInt(String key, int defaultValue) {
        return has(key) ? json.get(key).getAsInt() : defaultValue;
    }

    /** Returns the meta data as a JSON dictionary. */
    public JsonObject getJson() {
        return json;
    }
}
