package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(config);
    }

    public static Config fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, Config.class);
    }

    @Override
    public String getFileName() {
        return "NZSHM_config.json";
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
