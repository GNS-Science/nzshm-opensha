package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

public class FaultSectionProperties implements FileBackedModule {

    public Map<Integer, Map<String, Object>> data = new HashMap<>();

    public FaultSectionProperties() {}

    public void set(int sectionId, String property, Object value) {
        Map<String, Object> properties =
                data.computeIfAbsent(sectionId, k -> new LinkedHashMap<>());
        properties.put(property, value);
    }

    public Map<String, Object> get(int sectionId) {
        return data.get(sectionId);
    }

    public Object get(int sectionId, String property) {
        Map<String, Object> properties = data.get(sectionId);
        if (properties != null) {
            return properties.get(property);
        }
        return null;
    }

    @Override
    public String getFileName() {
        return "NZSHM_FaultSectionProperties.json";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(data);
        out.write(json.getBytes());
        out.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String json = new String(bytes, StandardCharsets.UTF_8);
        Gson gson = new Gson();
        data = gson.fromJson(json, Map.class);
    }

    @Override
    public String getName() {
        return "FaultSectionProperties";
    }
}
