package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

/**
 * Persists the {@link NZSHM22_AbstractRuptureSetBuilder} that produced a rupture set alongside it,
 * so that the exact builder configuration can be reconstituted later.
 */
public class NZSHM22_RuptureSetBuilderModule implements FileBackedModule {

    private NZSHM22_AbstractRuptureSetBuilder builder;

    public NZSHM22_RuptureSetBuilderModule() {}

    public NZSHM22_RuptureSetBuilderModule(NZSHM22_AbstractRuptureSetBuilder builder) {
        this.builder = builder;
    }

    public NZSHM22_AbstractRuptureSetBuilder getBuilder() {
        return builder;
    }

    protected String toJson() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("class", builder.getClass().getName());
        envelope.add("builder", JsonParser.parseString(builder.toJson()));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(envelope);
    }

    protected static NZSHM22_AbstractRuptureSetBuilder fromJson(String json) {
        try {
            JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
            String className = envelope.get("class").getAsString();
            String builderJson = envelope.get("builder").toString();
            Class<?> builderClass = Class.forName(className);
            // Note that the cached builders of NZSHM22_PythonGateway inherit fromJson from their
            // parent, so they are reconstituted as their parent class. They only add caching, no
            // configuration.
            Method fromJson = builderClass.getMethod("fromJson", String.class);
            return (NZSHM22_AbstractRuptureSetBuilder) fromJson.invoke(null, builderJson);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to reconstitute rupture set builder from JSON", e);
        }
    }

    @Override
    public String getFileName() {
        return "NZSHM22_ruptureSetBuilder.json";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(toJson());
        writer.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String data = new String(bytes, StandardCharsets.UTF_8);
        builder = fromJson(data);
    }

    @Override
    public String getName() {
        return "NZSHM22 Rupture Set Builder Config";
    }
}
