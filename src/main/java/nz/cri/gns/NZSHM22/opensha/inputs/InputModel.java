package nz.cri.gns.NZSHM22.opensha.inputs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads input model files (fault models, deformation models, ...) from the main branch of
 * https://github.com/voj/nzshm-input-models. Files are downloaded once per run into {@link
 * #CACHE_DIR}.
 */
public class InputModel {

    static final String BASE_URL =
            "https://raw.githubusercontent.com/voj/nzshm-input-models/main/models/";

    /** Suffix appended to an input model file's path to get its meta data file. */
    public static final String META_DATA_SUFFIX = ".meta.json";

    /** Temporary folder holding downloaded files, as {@code <path>}. */
    public static final Path CACHE_DIR;

    static {
        try {
            CACHE_DIR = Files.createTempDirectory("nzshm-input-models");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    private InputModel() {}

    /**
     * Opens an input model file, downloading it if necessary.
     *
     * @param path path below the repo's models directory, e.g. "faultModels/cfm_1_0A_all.xml"
     * @return the file's content, or null if the file does not exist
     */
    public static InputStream getStream(String path) {
        try {
            Path file = CACHE_DIR.resolve(path);
            if (!Files.isRegularFile(file) && !download(path, file)) {
                return null;
            }
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads the meta data of an input model file, stored in a file named path + {@value
     * #META_DATA_SUFFIX}.
     *
     * @param path path below the repo's models directory, e.g. "faultModels/cfm_1_0A_all.xml"
     * @return the meta data, or null if there is no meta data file
     */
    public static InputModelMetaData getMetaData(String path) {
        InputStream in = getStream(path + META_DATA_SUFFIX);
        if (in == null) {
            return null;
        }
        try {
            return InputModelMetaData.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Downloads the file to target. Returns false if it does not exist on GitHub. */
    static boolean download(String path, Path target) throws IOException {
        String url = BASE_URL + path;
        Files.createDirectories(target.getParent());
        // Download to a temp file and move it into place, so that another thread never reads a
        // partially downloaded file.
        Path tmp = Files.createTempFile(target.getParent(), null, ".part");
        try {
            HttpResponse<Path> response =
                    CLIENT.send(
                            HttpRequest.newBuilder(URI.create(url)).build(),
                            HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted fetching " + url, e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
