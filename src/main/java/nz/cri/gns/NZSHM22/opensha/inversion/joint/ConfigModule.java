package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

public class ConfigModule implements FileBackedModule {

    Config config;

    public ConfigModule() {
        config = new Config();
    }

    public ConfigModule(Config config) {
        this.config = config;
    }

    public Config getConfig() {
        return config;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(config);
    }

    public static Config fromJson(String json) {
        // Set to lenient to allow comments.
        // It's a bit too lenient for us, but at least comments are parsed correctly out of the box.
        Gson gson = new GsonBuilder().setLenient().create();
        return gson.fromJson(json, Config.class);
    }

    @Override
    public String getFileName() {
        return "NZSHM_config.jsonc";
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
        config = fromJson(data);
    }

    @Override
    public String getName() {
        return "NZSHM Config";
    }
}
