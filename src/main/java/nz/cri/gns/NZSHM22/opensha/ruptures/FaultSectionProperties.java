package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

public class FaultSectionProperties implements FileBackedModule {

    protected List<Map<String, Object>> data = new ArrayList<>();

    public FaultSectionProperties() {}

    public void set(int sectionId, String property, Object value) {
        while (data.size() <= sectionId) {
            data.add(null);
        }
        Map<String, Object> properties = data.get(sectionId);
        if (properties == null) {
            properties = new LinkedHashMap<>();
            data.set(sectionId, properties);
        }
        properties.put(property, value);
    }

    public Map<String, Object> get(int sectionId) {
        if (data.size() < sectionId + 1) {
            return null;
        }
        return data.get(sectionId);
    }

    public Object get(int sectionId, String property) {
        Map<String, Object> properties = get(sectionId);
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
        data = gson.fromJson(json, List.class);
    }

    @Override
    public String getName() {
        return "FaultSectionProperties";
    }
}
