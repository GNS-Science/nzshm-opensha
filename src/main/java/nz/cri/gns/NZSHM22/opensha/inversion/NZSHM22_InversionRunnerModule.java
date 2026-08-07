package nz.cri.gns.NZSHM22.opensha.inversion;

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
 * Persists the {@link NZSHM22_AbstractInversionRunner} that produced a solution alongside it, so
 * that the exact runner configuration can be reconstituted later.
 */
public class NZSHM22_InversionRunnerModule implements FileBackedModule {

    private NZSHM22_AbstractInversionRunner runner;

    public NZSHM22_InversionRunnerModule() {}

    public NZSHM22_InversionRunnerModule(NZSHM22_AbstractInversionRunner runner) {
        this.runner = runner;
    }

    public NZSHM22_AbstractInversionRunner getRunner() {
        return runner;
    }

    protected String toJson() {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("class", runner.getClass().getName());
        envelope.add("runner", JsonParser.parseString(runner.toJson()));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(envelope);
    }

    protected static NZSHM22_AbstractInversionRunner fromJson(String json) {
        try {
            JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
            String className = envelope.get("class").getAsString();
            String runnerJson = envelope.get("runner").toString();
            Class<?> runnerClass = Class.forName(className);
            Method fromJson = runnerClass.getMethod("fromJson", String.class);
            return (NZSHM22_AbstractInversionRunner) fromJson.invoke(null, runnerJson);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to reconstitute inversion runner from JSON", e);
        }
    }

    @Override
    public String getFileName() {
        return "NZSHM22_runner.json";
    }

    @Override
    public void writeToStream(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out);
        writer.write(toJson());
        writer.flush();
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String data = new String(bytes, StandardCharsets.UTF_8);
        runner = fromJson(data);
    }

    @Override
    public String getName() {
        return "NZSHM22 Inversion Runner Config";
    }
}
