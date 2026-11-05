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

    /** Prefix marking a path as a local file rather than a file in the GitHub repo. */
    public static final String FILE_PREFIX = "file://";

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
     * Builds the path of an input model file. Local files (starting with {@value #FILE_PREFIX}) are
     * returned unchanged.
     *
     * @param dir folder below the repo's models directory, e.g. "faultModels/"
     * @param fileName file name below dir, or a local file such as "file://C:/models/cfm.xml"
     * @return the path to pass to {@link #getStream(String)} or {@link #getMetaData(String)}
     */
    public static String resolve(String dir, String fileName) {
        return fileName.startsWith(FILE_PREFIX) ? fileName : dir + fileName;
    }

    /**
     * Opens an input model file, downloading it if necessary. Paths starting with {@value
     * #FILE_PREFIX} are read from the local file system instead.
     *
     * @param path path below the repo's models directory, e.g. "faultModels/cfm_1_0A_all.xml", or a
     *     local file such as "file://C:/models/cfm.xml"
     * @return the file's content, or null if the file does not exist
     */
    public static InputStream getStream(String path) {
        try {
            if (path.startsWith(FILE_PREFIX)) {
                Path local = Path.of(path.substring(FILE_PREFIX.length()));
                return Files.isRegularFile(local) ? Files.newInputStream(local) : null;
            }
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
     * @param path path below the repo's models directory, e.g. "faultModels/cfm_1_0A_all.xml", or a
     *     local file such as "file://C:/models/cfm.xml"
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
